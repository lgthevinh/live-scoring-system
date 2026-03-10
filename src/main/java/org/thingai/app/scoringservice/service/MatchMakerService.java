package org.thingai.app.scoringservice.service;

import org.thingai.base.log.ILog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MatchMakerService {
    private static final String TAG = "MatchMakerService";
    private String binPath;
    private String outPath;

    public void setBinPath(String path) {
        this.binPath = path;
        ILog.d(TAG, "Binary path set to: " + this.binPath);
    }

    public String getBinPath() {
        return binPath;
    }

    public void setOutPath(String path) {
        this.outPath = path;
        ILog.d(TAG, "Output path set to: " + this.outPath);
    }

    public String getOutPath() {
        return outPath;
    }

    /**
     * Run the external match maker tool and write its stdout directly to a file if outPath points to a file.
     * Returns the process exit code (0 = success).
     */
    public int generateMatchSchedule(int rounds, int numberOfTeams, int allianceSize) {
        if (binPath == null || binPath.isBlank() || outPath == null || outPath.isBlank()) {
            ILog.e(TAG, "Binary path or output path is not set.");
            return -1;
        }

        try {
            Path bin = Paths.get(binPath).toAbsolutePath().normalize();
            if (!Files.exists(bin) || !Files.isRegularFile(bin)) {
                ILog.e(TAG, "Binary not found or not a file: " + bin);
                return -2;
            }
            tryEnsureExecutable(bin);

            Path out = Paths.get(outPath).toAbsolutePath().normalize();

            Path workingDir;
            Path stdoutFile = null;

            if (Files.exists(out) && Files.isDirectory(out)) {
                workingDir = out;
            } else {
                String fileName = out.getFileName() != null ? out.getFileName().toString() : "";
                boolean looksLikeFile = fileName.contains("."); // treat as file if it has an extension
                if (looksLikeFile) {
                    Path parent = out.getParent();
                    if (parent == null) {
                        ILog.e(TAG, "Output path looks like a file but has no parent directory: " + out);
                        return -3;
                    }
                    Files.createDirectories(parent);
                    workingDir = parent;
                    stdoutFile = out; // write stdout directly to this file
                } else {
                    Files.createDirectories(out);
                    workingDir = out;
                }
            }

            List<String> cmd = new ArrayList<>();
            cmd.add(bin.toString());
            cmd.add("-t"); cmd.add(String.valueOf(numberOfTeams));
            cmd.add("-r"); cmd.add(String.valueOf(rounds));
            cmd.add("-a"); cmd.add(String.valueOf(allianceSize));

            ILog.d(TAG, "Working directory: " + workingDir);
            ILog.d(TAG, "Executing: " + String.join(" ", cmd));
            if (stdoutFile != null) {
                ILog.d(TAG, "Stdout will be written to: " + stdoutFile);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);

            // Critical change: redirect stdout directly to the target file so it's complete when process exits.
            if (stdoutFile != null) {
                pb.redirectOutput(stdoutFile.toFile());
            }

            Process process = pb.start();

            // If not writing to a file, stream logs (rare case)
            Thread logThread = null;
            if (stdoutFile == null) {
                logThread = new Thread(() -> streamToLogOnly(process.getInputStream()));
                logThread.setName("matchmaker-stdout");
                logThread.start();
            }

            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                ILog.e(TAG, "Match maker timed out and was terminated.");
                return -4;
            }

            int exitCode = process.exitValue();
            ILog.d(TAG, "Match maker finished with exit code: " + exitCode);

            if (logThread != null) {
                try { logThread.join(5000); } catch (InterruptedException ignored) {}
            }

            return exitCode;
        } catch (IOException e) {
            ILog.e(TAG, "IO error running match maker: " + e.getMessage());
            return -5;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ILog.e(TAG, "Interrupted while waiting for match maker: " + e.getMessage());
            return -6;
        }
    }

    private void streamToLogOnly(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ILog.d(TAG, "[proc] " + line);
            }
        } catch (IOException e) {
            ILog.w(TAG, "Failed to stream process output: " + e.getMessage());
        }
    }

    private void tryEnsureExecutable(Path bin) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(bin);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(bin, perms);
        } catch (UnsupportedOperationException ignored) {
            // Windows or non-POSIX FS
        } catch (IOException ex) {
            ILog.w(TAG, "Unable to adjust executable permissions: " + ex.getMessage());
        }
    }
}