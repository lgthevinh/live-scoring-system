package org.thingai.app.controller;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.entity.team.Team;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.thingai.app.controller.utils.ResponseEntityUtil.createErrorResponse;
import static org.thingai.app.controller.utils.ResponseEntityUtil.getObjectResponse;

@RestController
@RequestMapping("/api/team")
public class TeamController {

    @PostMapping("/create")
    public ResponseEntity<Object> createTeam(@RequestBody Map<String, Object> requestBody) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        try {
            Object teamIdObj = requestBody.get("teamId");
            Object teamNameObj = requestBody.get("teamName");
            Object teamSchoolObj = requestBody.get("teamSchool");
            Object teamRegionObj = requestBody.get("teamRegion");

            if (teamIdObj == null || teamNameObj == null || teamSchoolObj == null || teamRegionObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "All fields are required"));
            }

            String teamId = teamIdObj.toString();
            String teamName = teamNameObj.toString();
            String teamSchool = teamSchoolObj.toString();
            String teamRegion = teamRegionObj.toString();

            ScoringService.teamHandler().addTeam(teamId, teamName, teamSchool, teamRegion, new RequestCallback<Team>() {
                @Override
                public void onSuccess(Team team, String message) {
                    future.complete(ResponseEntity.status(HttpStatus.CREATED).body(team));
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    future.complete(createErrorResponse(errorCode, errorMessage));
                }
            });
        } catch (Exception e) {
            future.complete(ResponseEntity.badRequest().body(Map.of("error", "Invalid request format: " + e.getMessage())));
        }
        return getObjectResponse(future);
    }

    @GetMapping("/list")
    public ResponseEntity<Object> listTeams() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.teamHandler().listTeams(new RequestCallback<Team[]>() {
            @Override
            public void onSuccess(Team[] teams, String message) {
                future.complete(ResponseEntity.ok(teams));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });
        return getObjectResponse(future);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getTeam(@PathVariable String id) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.teamHandler().getTeamById(id, new RequestCallback<Team>() {
            @Override
            public void onSuccess(Team team, String message) {
                future.complete(ResponseEntity.ok(team));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });
        return getObjectResponse(future);
    }

    @PutMapping("/update")
    public ResponseEntity<Object> updateTeam(@RequestBody Team team) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.teamHandler().updateTeam(team, new RequestCallback<Team>() {
            @Override
            public void onSuccess(Team updatedTeam, String message) {
                future.complete(ResponseEntity.ok(updatedTeam));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });
        return getObjectResponse(future);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Object> deleteTeam(@PathVariable String id) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.teamHandler().deleteTeam(id, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void result, String message) {
                future.complete(ResponseEntity.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });
        return getObjectResponse(future);
    }

    @PostMapping("/bulk-create")
    public ResponseEntity<Object> bulkCreateTeams(@RequestBody Map<String, Object> requestBody) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        try {
            Object teamsObj = requestBody.get("teams");
            if (teamsObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Teams array is required"));
            }

            ObjectMapper mapper = new ObjectMapper();
            Team[] teamsList;
            try {
                teamsList = mapper.convertValue(teamsObj, Team[].class);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid team data format"));
            }
            ScoringService.teamHandler().addTeams(teamsList, new RequestCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean teams, String message) {
                    future.complete(ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", message)));
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    future.complete(createErrorResponse(errorCode, errorMessage));
                }
            });
        } catch (Exception e) {
            future.complete(ResponseEntity.badRequest().body(Map.of("error", "Invalid request format: " + e.getMessage())));
        }
        return getObjectResponse(future);
    }

    @GetMapping("/export")
    public ResponseEntity<Object> exportTeams() {
        try {
            if (ScoringService.teamHandler() == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "Team handler not initialized. Please set an active event first."));
            }

            final Team[][] teamsResult = new Team[1][];
            final String[] errorMessage = new String[1];
            final boolean[] completed = {false};

            ScoringService.teamHandler().listTeams(new RequestCallback<Team[]>() {
                @Override
                public void onSuccess(Team[] teams, String message) {
                    teamsResult[0] = teams;
                    completed[0] = true;
                }

                @Override
                public void onFailure(int errorCode, String errorMsg) {
                    errorMessage[0] = errorMsg;
                    completed[0] = true;
                }
            });

            // Wait for callback with timeout
            int timeout = 0;
            while (!completed[0] && timeout < 100) {
                Thread.sleep(10);
                timeout++;
            }

            if (!completed[0]) {
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                        .body(Map.of("error", "Request timeout"));
            }

            if (errorMessage[0] != null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", errorMessage[0]));
            }

            StringBuilder csv = new StringBuilder();
            csv.append("Team ID,Team Name,School,Region\n");

            if (teamsResult[0] != null) {
                for (Team team : teamsResult[0]) {
                    csv.append(escapeCsv(team.getTeamId())).append(",")
                            .append(escapeCsv(team.getTeamName())).append(",")
                            .append(escapeCsv(team.getTeamSchool())).append(",")
                            .append(escapeCsv(team.getTeamRegion())).append("\n");
                }
            }

            byte[] csvBytes = csv.toString().getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment", "teams.csv");
            headers.setContentLength(csvBytes.length);

            return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Export failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> importTeams(@RequestParam("file") MultipartFile file) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        // Validate file size (max 1MB)
        long maxFileSize = 1024 * 1024; // 1MB
        if (file.getSize() > maxFileSize) {
            return ResponseEntity.badRequest().body(Map.of("error", "File too large. Maximum size is 1MB"));
        }

        // Validate file type/extension
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Filename is required"));
        }

        String lowercaseFilename = filename.toLowerCase();
        boolean isCsv = lowercaseFilename.endsWith(".csv");
        boolean isXlsx = lowercaseFilename.endsWith(".xlsx");
        if (!isCsv && !isXlsx) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only CSV and XLSX files are allowed"));
        }

        try {
            List<Team> teams;
            if (isCsv) {
                teams = parseCsvFile(file);
            } else {
                teams = parseXlsxFile(file);
            }

            if (teams.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No valid team data found in file"));
            }

            Team[] teamsArray = teams.toArray(new Team[0]);
            ScoringService.teamHandler().addTeams(teamsArray, new RequestCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result, String message) {
                    future.complete(ResponseEntity.status(HttpStatus.CREATED)
                            .body(Map.of("message", "Imported " + teams.size() + " teams successfully")));
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    future.complete(createErrorResponse(errorCode, errorMessage));
                }
            });

        } catch (Exception e) {
            future.complete(ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to process CSV file: " + e.getMessage())));
        }

        return getObjectResponse(future);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    private List<Team> parseCsvFile(MultipartFile file) throws Exception {
        List<Team> teams = new ArrayList<>();
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().collect(Collectors.toList());
        }

        if (lines.isEmpty()) {
            return teams;
        }

        // Skip header line
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) continue;

            String[] parts = parseCsvLine(line);
            if (parts.length >= 4) {
                Team team = new Team();
                team.setTeamId(parts[0].trim());
                team.setTeamName(parts[1].trim());
                team.setTeamSchool(parts[2].trim());
                team.setTeamRegion(parts[3].trim());
                teams.add(team);
            }
        }
        return teams;
    }

    private List<Team> parseXlsxFile(MultipartFile file) throws Exception {
        List<Team> teams = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            // Skip header row
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                // Skip empty rows
                if (row.getCell(0) == null && row.getCell(1) == null) {
                    continue;
                }

                // Read cells: A=Team ID, B=Team Name, C=School, D=Region
                String teamId = getCellValue(row.getCell(0));
                String teamName = getCellValue(row.getCell(1));
                String school = getCellValue(row.getCell(2));
                String region = getCellValue(row.getCell(3));

                // Skip rows with empty required fields
                if (teamId == null || teamId.trim().isEmpty()) {
                    continue;
                }

                Team team = new Team();
                team.setTeamId(teamId.trim());
                team.setTeamName(teamName != null ? teamName.trim() : "");
                team.setTeamSchool(school != null ? school.trim() : "");
                team.setTeamRegion(region != null ? region.trim() : "");
                teams.add(team);
            }
        }
        return teams;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // Format numeric values without decimal if it's an integer
                double value = cell.getNumericCellValue();
                if (value == Math.floor(value)) {
                    return String.valueOf((long) value);
                }
                return String.valueOf(value);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }
}
