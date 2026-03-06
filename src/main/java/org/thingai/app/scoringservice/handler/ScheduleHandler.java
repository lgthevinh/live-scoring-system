package org.thingai.app.scoringservice.handler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.define.MatchType;
import org.thingai.app.scoringservice.entity.match.AllianceTeam;
import org.thingai.app.scoringservice.entity.match.Match;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.entity.team.Team;
import org.thingai.app.scoringservice.entity.time.TimeBlock;
import org.thingai.app.scoringservice.repository.LocalRepository;
import org.thingai.base.log.ILog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScheduleHandler {
    private static final String TAG = "ScheduleHandler";

    private final MatchMakerHandler matchMakerHandler = new MatchMakerHandler();

    public ScheduleHandler() {
        String osName = System.getProperty("os.name").toLowerCase();
        Path binary = Paths.get("binary");
        if (osName.contains("win")) {
            ILog.d("ScheduleHandler", "Detected Windows OS for MatchMakerHandler.");
            this.matchMakerHandler.setBinPath(binary.toAbsolutePath() + "/MatchMaker.exe");
        } else if (osName.contains("mac")) {
            ILog.d("ScheduleHandler", "Detected macOS for MatchMakerHandler.");
            this.matchMakerHandler.setBinPath(binary.toAbsolutePath() + "/MatchMaker_mac");
        } else {
            ILog.d("ScheduleHandler", "Assuming Linux OS for MatchMakerHandler.");
            this.matchMakerHandler.setBinPath(binary.toAbsolutePath() + "/MatchMaker");
        }

        Path dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) {
            try {
                Files.createDirectories(dataDir);
                ILog.d("ScheduleHandler", "Created data directory at: " + dataDir.toAbsolutePath());
            } catch (Exception e) {
                ILog.e("ScheduleHandler", "Error creating data directory: " + e.getMessage());
            }
        }

        Path outPath = dataDir.resolve("match_schedule.txt");
        if (!Files.exists(outPath)) {
            try {
                Files.createFile(outPath);
            } catch (Exception e) {
                ILog.e("ScheduleHandler", "Error creating match schedule output file: " + e.getMessage());
            }
        }

        String outDir = outPath.toAbsolutePath().toString();
        ILog.d("ScheduleHandler", "Match schedule output path set to: " + outDir);
        this.matchMakerHandler.setOutPath(outDir);
    }

    /**
     * V2 schedule generator:
     * - Uses external MatchMakerHandler to produce a schedule file.
     * - Parses "Match Schedule" lines into 2v2 team pairings.
     * - Maps team numbers in the file as 1-based indices into a SHUFFLED team list from DAO.
     * - Keeps existing time generation (start time, duration, TimeBlocks).
     */
    public void generateSchedule(int rounds, String startTime, int matchDuration, int fieldCount, TimeBlock[] timeBlocks, RequestCallback<Void> callback) {
        ILog.d("ScheduleHandler", "Generating match schedule V2 with rounds=" + rounds + ", start=" + startTime + ", duration=" + matchDuration + " min");
        try {
            // 1) Load teams and reset schedule-related tables and caches
            Team[] allTeams = LocalRepository.teamDao().listTeams();

            LocalRepository.matchDao().deleteAllMatch();
            LocalRepository.allianceTeamDao().deleteAllAllianceTeams();
            LocalRepository.scoreDao().deleteAllScores();

            if (allTeams == null || allTeams.length < 4) {
                callback.onFailure(ErrorCode.DAO_CREATE_FAILED, "Cannot generate schedule with fewer than 4 teams.");
                return;
            }

            // 2) Shuffle teams to form the 1..N mapping base (index 1 = shuffled[0])
            shuffleArray(allTeams);
            List<Team> shuffledTeams = Arrays.asList(allTeams);

            // 3) Run external generator (2 teams per alliance)
            int exitCode = matchMakerHandler.generateMatchSchedule(rounds, shuffledTeams.size(), 2);
            if (exitCode != 0) {
                callback.onFailure(ErrorCode.DAO_CREATE_FAILED, "MatchMaker.exe failed (exitCode=" + exitCode + "). Check matchmaker.log for details.");
                return;
            }

            // Read generated schedule from output file
            Path schedulePath = Paths.get(matchMakerHandler.getOutPath()).toAbsolutePath().normalize();
            if (Files.isDirectory(schedulePath)) {
                callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "OutPath is a directory. Please set MatchMakerHandler.outPath to the schedule file.");
                return;
            }

            // Small retry to ensure file is fully materialized
            List<String> lines = null;
            final long deadline = System.currentTimeMillis() + 2000; // up to 2 s
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(schedulePath)) {
                    try {
                        lines = Files.readAllLines(schedulePath);
                        if (!lines.isEmpty()) break;
                    } catch (IOException ignored) {}
                }
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
            if (lines == null || lines.isEmpty()) {
                callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Schedule file not readable at: " + schedulePath);
                return;
            }

            List<ParsedMatch> parsedMatches = parseMatchMakerSchedule(lines);
            if (parsedMatches.isEmpty()) {
                // Last defensive check: if we still don't see "Match Schedule", dump first few lines to logs
                ILog.w("ScheduleHandler", "Schedule header not found. First lines: " + String.join(" | ", lines.subList(0, Math.min(5, lines.size()))));
                callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "No matches parsed from schedule file. Ensure it contains a 'Match Schedule' section.");
                return;
            }

            // 5) Time keeping: same as V1 (apply TimeBlocks)
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            LocalDateTime currentTime = LocalDateTime.parse(startTime, timeFormatter);

            int matchNumber = 1;
            for (ParsedMatch pm : parsedMatches) {
                int fieldNumber = ((matchNumber - 1) % fieldCount) + 1;

                // Respect time blocks by skipping breaks
                if (timeBlocks != null) {
                    for (TimeBlock block : timeBlocks) {
                        LocalDateTime breakStart = LocalDateTime.parse(block.getStartTime(), timeFormatter);
                        long breakDuration = Long.parseLong(block.getDuration());
                        LocalDateTime breakEnd = breakStart.plusMinutes(breakDuration);

                        if (!currentTime.isBefore(breakStart) && currentTime.isBefore(breakEnd)) {
                            currentTime = breakEnd;
                        }
                    }
                }

                if (pm.red.length < 2 || pm.blue.length < 2) {
                    ILog.w("ScheduleHandler", "Skipping malformed match line: " + Arrays.toString(pm.red) + " vs " + Arrays.toString(pm.blue));
                    continue;
                }

                // Map team numbers (1-based) -> shuffled team IDs
                String[] redTeamIds = new String[] {
                        mapTeamIndexToId(shuffledTeams, pm.red[0]),
                        mapTeamIndexToId(shuffledTeams, pm.red[1])
                };
                String[] blueTeamIds = new String[] {
                        mapTeamIndexToId(shuffledTeams, pm.blue[0]),
                        mapTeamIndexToId(shuffledTeams, pm.blue[1])
                };

                HashMap<String, Boolean> surrogateMap = new HashMap<>();
                for (int tIdx : pm.red) {
                    String teamId = mapTeamIndexToId(shuffledTeams, tIdx);
                    surrogateMap.put(teamId, pm.surrogateMap.getOrDefault(tIdx, false));
                }
                for (int tIdx : pm.blue) {
                    String teamId = mapTeamIndexToId(shuffledTeams, tIdx);
                    surrogateMap.put(teamId, pm.surrogateMap.getOrDefault(tIdx, false));
                }

                ILog.d("ScheduleHandler", Arrays.toString(redTeamIds) + " vs " + Arrays.toString(blueTeamIds) + " at " + currentTime.format(timeFormatter));

                // Create the match and scores
                createMatchInternal(MatchType.QUALIFICATION, matchNumber, fieldNumber, currentTime.format(timeFormatter), redTeamIds, blueTeamIds, surrogateMap);

                // Advance time for next match
                currentTime = currentTime.plusMinutes(matchDuration);
                matchNumber++;
            }

            callback.onSuccess(null, "Match schedule generated successfully by MatchMaker (shuffled mapping) and times assigned.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_CREATE_FAILED, "Failed to generate match schedule: " + e.getMessage());
        }
    }

    public void generatePlayoffSchedule(int playoffType, int fieldCount, AllianceTeam[] allianceTeams, String startTime, int matchDuration, TimeBlock[] timeBlocks, RequestCallback<Void> callback) {

    }

    /**
     * Parse the "Match Schedule" section from the MatchMaker .txt output.
     * Expected lines like: "  1:    4     7     5     8 "
     * Optional '*' after a team number denotes surrogate; ignored here.
     */
    private List<ParsedMatch> parseMatchMakerSchedule(List<String> lines) {
        List<ParsedMatch> matches = new ArrayList<>();
        boolean inSchedule = false;
        Pattern linePattern = Pattern.compile("^\\s*(\\d+)\\s*:\\s*(\\d+\\*?)\\s+(\\d+\\*?)\\s+(\\d+\\*?)\\s+(\\d+\\*?).*$");

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();

            if (!inSchedule) {
                if (line.equalsIgnoreCase("Match Schedule")) {
                    inSchedule = true;
                }
                continue;
            }

            if (line.isEmpty() || line.startsWith("------")) {
                continue;
            }
            if (line.toLowerCase().startsWith("schedule statistics")) {
                break;
            }

            Matcher m = linePattern.matcher(raw);
            if (m.matches()) {
                HashMap<Integer, Boolean> surrogateMap = new HashMap<>();
                int t1 = parseTeamIndex(m.group(2));
                int t2 = parseTeamIndex(m.group(3));
                int t3 = parseTeamIndex(m.group(4));
                int t4 = parseTeamIndex(m.group(5));

                if (t1 == -1 || t2 == -1 || t3 == -1 || t4 == -1) {
                    ILog.w("ScheduleHandler", "Failed to parse team indices from line: " + raw);
                    continue;
                }

                if (m.group(2).endsWith("*")) surrogateMap.put(t1, true);
                if (m.group(3).endsWith("*")) surrogateMap.put(t2, true);
                if (m.group(4).endsWith("*")) surrogateMap.put(t3, true);
                if (m.group(5).endsWith("*")) surrogateMap.put(t4, true);

                ParsedMatch pm = new ParsedMatch();
                pm.red = new int[]{t1, t2};
                pm.blue = new int[]{t3, t4};
                pm.surrogateMap = surrogateMap;
                matches.add(pm);
            }
        }
        return matches;
    }

    private int parseTeamIndex(String token) {
        ILog.d("ScheduleHandler", "Parsing team index from token: " + token);
        String digits = token.replace("*", "").trim(); // remove surrogate marker
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String mapTeamIndexToId(List<Team> shuffledTeams, int idx1Based) throws Exception {
        if (idx1Based < 1 || idx1Based > shuffledTeams.size()) {
            throw new Exception("Team index out of bounds in schedule: " + idx1Based);
        }
        return shuffledTeams.get(idx1Based - 1).getTeamId();
    }

    // Helper holder for parsed match
    private static class ParsedMatch {
        int[] red;
        int[] blue;
        HashMap<Integer, Boolean> surrogateMap;
    }

    private void createMatchInternal(int matchType, int matchNumber, int field, String matchStartTime, String[] redTeamIds, String[] blueTeamIds, HashMap<String, Boolean> surrogateMap) throws Exception {
        // Handle duplicate team IDs
        Set<String> uniqueReds = new HashSet<>(Arrays.asList(redTeamIds));
        Set<String> uniqueBlues = new HashSet<>(Arrays.asList(blueTeamIds));
        if (uniqueReds.size() < redTeamIds.length || uniqueBlues.size() < blueTeamIds.length) {
            throw new Exception("Duplicate team IDs detected in match creation.");
        }

        Match match = new Match();
        match.setMatchType(matchType);
        match.setMatchNumber(matchNumber);
        match.setMatchStartTime(matchStartTime);
        match.setFieldNumber(field);

        String matchPrefix = switch (matchType) {
            case MatchType.QUALIFICATION -> "Q";
            case MatchType.PLAYOFF -> "P";
            case MatchType.SEMIFINAL -> "SF";
            case MatchType.FINAL -> "F";
            default -> "";
        };

        String matchCode = matchPrefix + matchNumber;
        match.setMatchCode(matchCode);
        match.setId(matchCode);

        String blueAllianceId = matchCode + "_B";
        String redAllianceId = matchCode + "_R";

        LocalRepository.allianceTeamDao().deleteAllianceTeamsByAllianceId(redAllianceId);
        LocalRepository.allianceTeamDao().deleteAllianceTeamsByAllianceId(blueAllianceId);

        for (String teamId : uniqueReds) {
            AllianceTeam team = new AllianceTeam();
            team.setTeamId(teamId);
            team.setAllianceId(redAllianceId);
            team.setSurrogate(surrogateMap.get(teamId));
            ILog.d("ScheduleHandler", "Inserting red alliance team: " + teamId + " surrogate=" + surrogateMap.get(teamId));
            LocalRepository.allianceTeamDao().insertAllianceTeam(team);
        }

        for (String teamId : uniqueBlues) {
            AllianceTeam team = new AllianceTeam();
            team.setTeamId(teamId);
            team.setAllianceId(blueAllianceId);
            team.setSurrogate(surrogateMap.get(teamId));
            ILog.d("ScheduleHandler", "Inserting blue alliance team: " + teamId + " surrogate=" + surrogateMap.get(teamId));
            LocalRepository.allianceTeamDao().insertAllianceTeam(team);
        }

        Score redScore = ScoringHandler.factoryScore();
        redScore.setAllianceId(redAllianceId);

        Score blueScore = ScoringHandler.factoryScore();
        blueScore.setAllianceId(blueAllianceId);

        LocalRepository.matchDao().insertMatch(match);
        LocalRepository.scoreDao().insertScore(redScore);
        LocalRepository.scoreDao().insertScore(blueScore);
    }

    // Utility methods
    private <T> void shuffleArray(T[] array) {
        Random rand = new Random();
        for (int i = array.length - 1; i > 0; i--) {
            int index = rand.nextInt(i + 1);
            T a = array[index];
            array[index] = array[i];
            array[i] = a;
        }
    }
}