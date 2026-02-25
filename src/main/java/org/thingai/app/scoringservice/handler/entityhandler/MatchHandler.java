package org.thingai.app.scoringservice.handler.entityhandler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.define.MatchType;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.config.DbMapEntity;
import org.thingai.app.scoringservice.entity.match.AllianceTeam;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.entity.team.Team;
import org.thingai.app.scoringservice.entity.time.TimeBlock;
import org.thingai.app.scoringservice.handler.MatchMakerHandler;
import org.thingai.base.cache.LRUCache;
import org.thingai.base.dao.Dao;
import org.thingai.app.scoringservice.entity.match.Match;
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

public class MatchHandler {
    private static final String TAG = "MatchHandler";

    private Dao dao;

    private final MatchMakerHandler matchMakerHandler = new MatchMakerHandler();

    // Caches are now injected via the constructor for better management.
    private LRUCache<String, Match> matchCache;
    private final LRUCache<String, AllianceTeam[]> allianceTeamCache;
    private final LRUCache<String, Team> teamCache;

    // Flag to indicate if the cache is stale and needs to be refreshed from the database.
    private boolean matchUpdateFlag = true; // Start as true to force initial loads.

    public MatchHandler(Dao dao, LRUCache<String, Match> matchCache, LRUCache<String, AllianceTeam[]> allianceTeamCache, LRUCache<String, Team> teamCache) {
        this.dao = dao;
        this.matchCache = matchCache;
        this.allianceTeamCache = allianceTeamCache;
        this.teamCache = teamCache;

        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            ILog.d("MatchHandler", "Detected Windows OS for MatchMakerHandler.");
            this.matchMakerHandler.setBinPath(Paths.get("binary").toAbsolutePath() + "/MatchMaker.exe");
        } else if (osName.contains("mac")) {
            ILog.d("MatchHandler", "Detected macOS for MatchMakerHandler.");
            this.matchMakerHandler.setBinPath(Paths.get("binary").toAbsolutePath() + "/MatchMaker_mac");
        } else {
            ILog.d("MatchHandler", "Assuming Linux OS for MatchMakerHandler.");
            this.matchMakerHandler.setBinPath(Paths.get("binary").toAbsolutePath() + "/MatchMaker");
        }

        Path dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) {
            try {
                Files.createDirectories(dataDir);
                ILog.d("MatchHandler", "Created data directory at: " + dataDir.toAbsolutePath());
            } catch (Exception e) {
                ILog.e("MatchHandler", "Error creating data directory: " + e.getMessage());
            }
        }

        Path outPath = dataDir.resolve("match_schedule.txt");
        if (!Files.exists(outPath)) {
            try {
                Files.createFile(outPath);
            } catch (Exception e) {
                ILog.e("MatchHandler", "Error creating match schedule output file: " + e.getMessage());
            }
        }

        String outDir = outPath.toAbsolutePath().toString();
        ILog.d("MatchHandler", "Match schedule output path set to: " + outDir);
        this.matchMakerHandler.setOutPath(outDir);

        // Get event match type from dao
        // try {
        //     DbMapEntity eventMatchType = dao.query(DbMapEntity.class, new String[]{"key"}, new String[]{"event_match_type_key"})[0];
        //     if (eventMatchType != null) {
        //         currentEventMatchType = Integer.parseInt(eventMatchType.getValue());
        //     } else {
        //         currentEventMatchType = MatchType.QUALIFICATION;
        //         dao.insertOrUpdate(new DbMapEntity("event_match_type_key", String.valueOf(currentEventMatchType)));
        //     }
        // } catch (Exception e) {
        //     currentEventMatchType = MatchType.QUALIFICATION;
        // }
    }

    // public void updateEventMatchType(int matchType) {
    //     currentEventMatchType = matchType;
    //     try {
    //         dao.insertOrUpdate(new DbMapEntity("event_match_type_key", String.valueOf(currentEventMatchType)));
    //     } catch (Exception e) {
    //         ILog.e("MatchHandler", "Failed to update event match type in DB: " + e.getMessage());
    //     }
    // }

    // Methods use outside system implementation
    public void createMatch(int matchType, int matchNumber, String matchStartTime, String[] redTeamIds, String[] blueTeamIds, RequestCallback<Match> callback) {
        try {
            ILog.d(TAG, "manual match creation");
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

            String matchCode = switch (matchType) {
                case MatchType.QUALIFICATION -> "Q" + matchNumber;
                case MatchType.PLAYOFF -> "P" + matchNumber;
                case MatchType.SEMIFINAL -> "SF" + matchNumber;
                case MatchType.FINAL -> "F" + matchNumber;
                default -> "";
            };
            match.setMatchCode(matchCode);
            match.setId(matchCode);

            String blueAllianceId = matchCode + "_B";
            String redAllianceId = matchCode + "_R";

            // Clear existing alliance teams if any
            dao.deleteByColumn(AllianceTeam.class, "allianceId", redAllianceId);
            dao.deleteByColumn(AllianceTeam.class, "allianceId", blueAllianceId);

            for (String teamId : uniqueReds) {
                AllianceTeam team = new AllianceTeam();
                team.setTeamId(teamId);
                team.setAllianceId(redAllianceId);
                dao.insertOrUpdate(team);
            }

            for (String teamId : uniqueBlues) {
                AllianceTeam team = new AllianceTeam();
                team.setTeamId(teamId);
                team.setAllianceId(blueAllianceId);
                dao.insertOrUpdate(team);
            }

            // Create scores (as per original code)
            Score redScore = ScoreHandler.factoryScore();
            redScore.setAllianceId(redAllianceId);

            Score blueScore = ScoreHandler.factoryScore();
            blueScore.setAllianceId(blueAllianceId);

            dao.insertOrUpdate(match);
            dao.insertOrUpdate(Score.class, redScore);
            dao.insertOrUpdate(Score.class, blueScore);

            ILog.d(TAG, "created match: " + matchCode, Arrays.toString(redTeamIds), Arrays.toString(blueTeamIds));
            callback.onSuccess(match, "Match created successfully.");
            setMatchUpdateFlag(true); // Invalidate cache
        } catch (Exception e) {
            ILog.e(TAG, "Error creating match: " + e.getMessage());
            callback.onFailure(ErrorCode.CREATE_FAILED, "Failed to create match: " + e.getMessage());
        }
    }

    public void getMatch(String matchId, RequestCallback<Match> callback) {
        if (isMatchUpdateFlag()) {
            Match cachedMatch = matchCache.get(matchId);
            if (cachedMatch != null) {
                callback.onSuccess(cachedMatch, "Match retrieved successfully from cache.");
                return;
            }
        }

        try {
            Match match = dao.query(Match.class, new String[]{"id"}, new String[]{matchId})[0];
            if (match != null) {
                matchCache.put(matchId, match); // Add to cache
                callback.onSuccess(match, "Match retrieved successfully.");
            } else {
                callback.onFailure(ErrorCode.NOT_FOUND, "Match not found.");
            }
        } catch (Exception e) {
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Failed to retrieve match: " + e.getMessage());
        }
    }

    public void getMatchDetail(String matchId, RequestCallback<MatchDetailDto> callback) {
        try {
            Match match = dao.query(Match.class, new String[]{"id"}, new String[]{matchId})[0];
            if (match == null) {
                callback.onFailure(ErrorCode.NOT_FOUND, "Match not found.");
                return;
            }

            String redAllianceId = match.getMatchCode() + "_R";
            String blueAllianceId = match.getMatchCode() + "_B";

            AllianceTeam[] redAllianceTeams = dao.query(AllianceTeam.class, new String[]{"allianceId"}, new String[]{redAllianceId});
            AllianceTeam[] blueAllianceTeams = dao.query(AllianceTeam.class, new String[]{"allianceId"}, new String[]{blueAllianceId});
            Team[] redTeams = Arrays.stream(redAllianceTeams)
                    .map(at -> {
                        try {
                            return dao.query(Team.class, new String[]{"id"}, new String[]{at.getTeamId()})[0];
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toArray(Team[]::new);
            Team[] blueTeams = Arrays.stream(blueAllianceTeams)
                    .map(at -> {
                        try {
                            return dao.query(Team.class, new String[]{"id"}, new String[]{at.getTeamId()})[0];
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toArray(Team[]::new);

            HashMap<String, Boolean> surrogateMap = new HashMap<>();
            for (AllianceTeam at : redAllianceTeams) {
                surrogateMap.put(at.getTeamId(), at.isSurrogate());
            }
            for (AllianceTeam at : blueAllianceTeams) {
                surrogateMap.put(at.getTeamId(), at.isSurrogate());
            }
            MatchDetailDto detailDto = new MatchDetailDto(match, redTeams, blueTeams, surrogateMap);
            callback.onSuccess(detailDto, "Match detail retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Failed to retrieve match detail: " + e.getMessage());
        }
    }

    public void updateMatch(Match match, RequestCallback<Match> callback) {
        try {
            dao.insertOrUpdate(match);
            matchCache.put(match.getId(), match);
            setMatchUpdateFlag(true);
            callback.onSuccess(match, "Match updated successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.UPDATE_FAILED, "Failed to update match: " + e.getMessage());
        }
    }

    public void deleteMatch(String matchId, RequestCallback<Void> callback) {
        try {
            dao.delete(Match.class, matchId);
            matchCache.remove(matchId);
            setMatchUpdateFlag(true);
            callback.onSuccess(null, "Match deleted successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DELETE_FAILED, "Failed to delete match: " + e.getMessage());
        }
    }

    public void listMatches(RequestCallback<Match[]> callback) {
        try {
            Match[] matches = dao.readAll(Match.class);
            matchCache.clear();
            for (Match match : matches) {
                matchCache.put(match.getId(), match);
            }
            setMatchUpdateFlag(false);
            callback.onSuccess(matches, "Matches retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Failed to retrieve matches: " + e.getMessage());
        }
    }

    public void listMatchesByType(int matchType, RequestCallback<Match[]> callback) {
        try {
            Match[] matches;
            if (matchType == MatchType.PLAYOFF) {
                matches = dao.query(Match.class, "SELECT * FROM match WHERE NOT matchType = 1");
            } else {
                matches = dao.query(Match.class, new String[]{"matchType"}, new String[]{String.valueOf(matchType)});
            }

            for (Match match : matches) {
                matchCache.put(match.getId(), match);
            }
            callback.onSuccess(matches, "Matches retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Failed to retrieve matches: " + e.getMessage());
        }
    }

    public void listMatchDetails(int matchType, boolean withScore, RequestCallback<MatchDetailDto[]> callback) {
        try {
            Match[] matches;
            if (matchType == MatchType.PLAYOFF) {
                matches = dao.query(Match.class, "SELECT * FROM match WHERE NOT matchType = 1");
            } else {
                matches = dao.query(Match.class, new String[]{"matchType"}, new String[]{String.valueOf(matchType)});
            }
            List<MatchDetailDto> detailsList = new ArrayList<>();

            for (Match match : matches) {
                matchCache.put(match.getId(), match);

                String redAllianceId = match.getMatchCode() + "_R";
                String blueAllianceId = match.getMatchCode() + "_B";

                // Use cache for alliance teams
                AllianceTeam[] redAllianceTeams = allianceTeamCache.get(redAllianceId);
                if (redAllianceTeams == null) {
                    redAllianceTeams = dao.query(AllianceTeam.class, new String[]{"allianceId"}, new String[]{redAllianceId});
                    allianceTeamCache.put(redAllianceId, redAllianceTeams);
                }

                AllianceTeam[] blueAllianceTeams = allianceTeamCache.get(blueAllianceId);
                if (blueAllianceTeams == null) {
                    blueAllianceTeams = dao.query(AllianceTeam.class, new String[]{"allianceId"}, new String[]{blueAllianceId});
                    allianceTeamCache.put(blueAllianceId, blueAllianceTeams);
                }

                HashMap<String, Boolean> surrogateMap = new HashMap<>();

                // Use cache for individual teams
                Team[] redTeams = Arrays.stream(redAllianceTeams)
                        .map(at -> {
                            Team team = teamCache.get(at.getTeamId());
                            if (team == null) {
                                team = dao.query(Team.class, new String[]{"id"}, new String[]{at.getTeamId()})[0];
                                if (team != null) teamCache.put(at.getTeamId(), team);
                            }

                            surrogateMap.put(at.getTeamId(), at.isSurrogate());
                            return team;
                        })
                        .filter(Objects::nonNull)
                        .toArray(Team[]::new);

                Team[] blueTeams = Arrays.stream(blueAllianceTeams)
                        .map(at -> {
                            Team team = teamCache.get(at.getTeamId());
                            if (team == null) {
                                team = dao.query(Team.class, new String[]{"id"}, new String[]{at.getTeamId()})[0];
                                if (team != null) teamCache.put(at.getTeamId(), team);
                            }

                            surrogateMap.put(at.getTeamId(), at.isSurrogate());
                            return team;
                        })
                        .filter(Objects::nonNull)
                        .toArray(Team[]::new);

                if (withScore) {
                    String redAllianceScoreId = match.getMatchCode() + "_R";
                    String blueAllianceScoreId = match.getMatchCode() + "_B";
                    Score redScore = dao.query(Score.class, new String[]{"id"}, new String[]{redAllianceScoreId})[0];
                    Score blueScore = dao.query(Score.class, new String[]{"id"}, new String[]{blueAllianceScoreId})[0];
                    detailsList.add(new MatchDetailDto(match, redTeams, blueTeams, redScore, blueScore, surrogateMap));
                    continue;
                }

                detailsList.add(new MatchDetailDto(match, redTeams, blueTeams, surrogateMap));
            }

            callback.onSuccess(detailsList.toArray(new MatchDetailDto[0]), "Match details retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Failed to retrieve match details: " + e.getMessage());
        }
    }

    /**
     * V2 schedule generator:
     * - Uses external MatchMakerHandler to produce a schedule file.
     * - Parses "Match Schedule" lines into 2v2 team pairings.
     * - Maps team numbers in the file as 1-based indices into a SHUFFLED team list from DAO.
     * - Keeps existing time generation (start time, duration, TimeBlocks).
     */
    public void generateMatchScheduleV2(int rounds, String startTime, int matchDuration, int fieldCount, TimeBlock[] timeBlocks, RequestCallback<Void> callback) {
        ILog.d("MatchHandler", "Generating match schedule V2 with rounds=" + rounds + ", start=" + startTime + ", duration=" + matchDuration + " min");
        try {
            // 1) Load teams and reset schedule-related tables and caches
            Team[] allTeams = dao.readAll(Team.class);

            dao.deleteAll(Match.class);
            dao.deleteAll(AllianceTeam.class);
            dao.deleteAll(Score.class);

            matchCache.clear();
            allianceTeamCache.clear();
            teamCache.clear();

            if (allTeams == null || allTeams.length < 4) {
                callback.onFailure(ErrorCode.CREATE_FAILED, "Cannot generate schedule with fewer than 4 teams.");
                return;
            }

            // 2) Shuffle teams to form the 1..N mapping base (index 1 = shuffled[0])
            shuffleArray(allTeams);
            List<Team> shuffledTeams = Arrays.asList(allTeams);

            // 3) Run external generator (2 teams per alliance)
            int exitCode = matchMakerHandler.generateMatchSchedule(rounds, shuffledTeams.size(), 2);
            if (exitCode != 0) {
                callback.onFailure(ErrorCode.CREATE_FAILED, "MatchMaker.exe failed (exitCode=" + exitCode + "). Check matchmaker.log for details.");
                return;
            }

            // Read generated schedule from output file
            Path schedulePath = Paths.get(matchMakerHandler.getOutPath()).toAbsolutePath().normalize();
            if (Files.isDirectory(schedulePath)) {
                callback.onFailure(ErrorCode.RETRIEVE_FAILED, "OutPath is a directory. Please set MatchMakerHandler.outPath to the schedule file.");
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
                callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Schedule file not readable at: " + schedulePath);
                return;
            }

            List<ParsedMatch> parsedMatches = parseMatchMakerSchedule(lines);
            if (parsedMatches.isEmpty()) {
                // Last defensive check: if we still don't see "Match Schedule", dump first few lines to logs
                ILog.w("MatchHandler", "Schedule header not found. First lines: " + String.join(" | ", lines.subList(0, Math.min(5, lines.size()))));
                callback.onFailure(ErrorCode.RETRIEVE_FAILED, "No matches parsed from schedule file. Ensure it contains a 'Match Schedule' section.");
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
                    ILog.w("MatchHandler", "Skipping malformed match line: " + Arrays.toString(pm.red) + " vs " + Arrays.toString(pm.blue));
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

                ILog.d("MatchHandler", Arrays.toString(redTeamIds) + " vs " + Arrays.toString(blueTeamIds) + " at " + currentTime.format(timeFormatter));

                // Create the match and scores
                createMatchInternal(MatchType.QUALIFICATION, matchNumber, fieldNumber, currentTime.format(timeFormatter), redTeamIds, blueTeamIds, surrogateMap);

                // Advance time for next match
                currentTime = currentTime.plusMinutes(matchDuration);
                matchNumber++;
            }

            setMatchUpdateFlag(true);
            callback.onSuccess(null, "Match schedule generated successfully by MatchMaker (shuffled mapping) and times assigned.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.CREATE_FAILED, "Failed to generate match schedule: " + e.getMessage());
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
                    ILog.w("MatchHandler", "Failed to parse team indices from line: " + raw);
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
        ILog.d("MatchHandler", "Parsing team index from token: " + token);
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

        dao.deleteByColumn(AllianceTeam.class, "allianceId", redAllianceId);
        dao.deleteByColumn(AllianceTeam.class, "allianceId", blueAllianceId);

        for (String teamId : uniqueReds) {
            AllianceTeam team = new AllianceTeam();
            team.setTeamId(teamId);
            team.setAllianceId(redAllianceId);
            team.setSurrogate(surrogateMap.get(teamId));
            ILog.d("MatchHandler", "Inserting red alliance team: " + teamId + " surrogate=" + surrogateMap.get(teamId));
            dao.insertOrUpdate(team);
        }

        for (String teamId : uniqueBlues) {
            AllianceTeam team = new AllianceTeam();
            team.setTeamId(teamId);
            team.setAllianceId(blueAllianceId);
            team.setSurrogate(surrogateMap.get(teamId));
            ILog.d("MatchHandler", "Inserting blue alliance team: " + teamId + " surrogate=" + surrogateMap.get(teamId));
            dao.insertOrUpdate(team);
        }

        Score redScore = ScoreHandler.factoryScore();
        redScore.setAllianceId(redAllianceId);

        Score blueScore = ScoreHandler.factoryScore();
        blueScore.setAllianceId(blueAllianceId);

        dao.insertOrUpdate(match);
        dao.insertOrUpdate(Score.class, redScore);
        dao.insertOrUpdate(Score.class, blueScore);
    }

    // Methods use inside system implementation
    public Match getMatchSync(String matchId) throws Exception {
        if (isMatchUpdateFlag()) {
            Match cachedMatch = matchCache.get(matchId);
            if (cachedMatch != null) {
                return cachedMatch;
            }
        }

        Match match = dao.query(Match.class, new String[]{"id"}, new String[]{matchId})[0];
        if (match != null) {
            matchCache.put(matchId, match); // Add to cache
            return match;
        } else {
            throw new Exception("Match not found.");
        }
    }

    public MatchDetailDto getMatchDetailSync(String matchId) throws Exception {
        Match match = dao.query(Match.class, new String[]{"id"}, new String[]{matchId})[0];
        if (match == null) {
            throw new Exception("Match not found.");
        }

        String redAllianceId = match.getMatchCode() + "_R";
        String blueAllianceId = match.getMatchCode() + "_B";

        AllianceTeam[] redAllianceTeams = dao.query(AllianceTeam.class, new String[]{"allianceId"}, new String[]{redAllianceId});
        AllianceTeam[] blueAllianceTeams = dao.query(AllianceTeam.class, new String[]{"allianceId"}, new String[]{blueAllianceId});
        Team[] redTeams = Arrays.stream(redAllianceTeams)
                .map(at -> {
                    try {
                        return dao.query(Team.class, new String[]{"id"}, new String[]{at.getTeamId()})[0];
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toArray(Team[]::new);
        Team[] blueTeams = Arrays.stream(blueAllianceTeams)
                .map(at -> {
                    try {
                        return dao.query(Team.class, new String[]{"id"}, new String[]{at.getTeamId()})[0];
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toArray(Team[]::new);

        HashMap<String, Boolean> surrogateMap = new HashMap<>();
        for (AllianceTeam at : redAllianceTeams) {
            if (at.isSurrogate()) {
                surrogateMap.put(at.getTeamId(), true);
            }
        }
        for (AllianceTeam at : blueAllianceTeams) {
            if (at.isSurrogate()) {
                surrogateMap.put(at.getTeamId(), true);
            }
        }
        return new MatchDetailDto(match, redTeams, blueTeams, surrogateMap);
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

    private boolean isMatchUpdateFlag() {
        return !matchUpdateFlag;
    }

    private void setMatchUpdateFlag(boolean matchUpdateFlag) {
        this.matchUpdateFlag = matchUpdateFlag;
        if (this.matchUpdateFlag) {
            // If the flag is set to dirty, clear all caches to force reloads.
            matchCache.clear();
            allianceTeamCache.clear();
            teamCache.clear();
        }
    }

    public void setMatchCache(LRUCache<String, Match> matchCache) {
        this.matchCache = matchCache;
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }
}
