package com.mikemybytes.jfr.pinning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class NativeMemoryTracker implements SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(NativeMemoryTracker.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int SNAPSHOT_PERIOD_IN_MINUTES = 5;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private String javaPid;
    private static Path tempDirPath;
    private String javaHome;
    private String jcmdPath;

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting NativeMemoryTracker");
            startNativeMemoryTracking();
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping NativeMemoryTracker");
            executorService.shutdown();
            try {
                executorService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        executorService.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void takeSnapshot() {
        try {
            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            String filename = "jfr-leak-diff-" + timestamp;
            logger.info("Running jcmd detail.diff command");
            ProcessBuilder processBuilder = new ProcessBuilder(jcmdPath, javaPid, "VM.native_memory", "detail.diff");
            processBuilder.directory(tempDirPath.toFile());
            processBuilder.redirectError(tempDirPath.resolve(filename + ".err").toFile());
            processBuilder.redirectOutput(tempDirPath.resolve(filename).toFile());
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("jcmd detail.diff command failed with exit code: {}", exitCode);
                System.exit(1);
            }
            logger.info("jcmd detail.diff executed, saved its output into file {} in directory: {}", filename,
                    tempDirPath);
        } catch (IOException | InterruptedException e) {
            logger.error("Error while executing jcmd command", e);
            System.exit(1);
        }
    }

    private void startNativeMemoryTracking() {
        initializeAndGetTempFolder();
        try {
            javaPid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            javaHome = System.getenv("JAVA_HOME");
            jcmdPath = Paths.get(javaHome, "bin", "jcmd").toString();
            logger.info("Using JAVA_HOME: {}", javaHome);
            logger.info("Using jcmd: {}", jcmdPath);
            logger.info("Using java PID: {}", javaPid);

            ProcessBuilder processBuilder = new ProcessBuilder(jcmdPath, javaPid, "VM.native_memory", "baseline");
            processBuilder.directory(tempDirPath.toFile());
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("jcmd baseline command failed with exit code: {}", exitCode);
                try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                    String outputLine;
                    while ((outputLine = outputReader.readLine()) != null) {
                        logger.error("stdout: {}", outputLine);
                    }

                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        logger.error("stderr: {}", errorLine);
                    }
                }
                System.exit(1);
            }

            logger.info("Executed jcmd baseline command in directory: {}", tempDirPath);

            logger.info("Scheduling a diff run every {} minute(s).", SNAPSHOT_PERIOD_IN_MINUTES);
            executorService.scheduleAtFixedRate(this::takeSnapshot, SNAPSHOT_PERIOD_IN_MINUTES,
                    SNAPSHOT_PERIOD_IN_MINUTES, TimeUnit.MINUTES);
        } catch (IOException | InterruptedException e) {
            logger.error("Error while executing jcmd command", e);
            System.exit(1);
        }
    }

    public static synchronized Path initializeAndGetTempFolder() {
        try {
            if (tempDirPath != null) {
                return tempDirPath;
            }

            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            String folderName = "jfr-leak-" + timestamp + "-";
            tempDirPath = Files.createTempDirectory(folderName);
            logger.info("Created temp directory: {}", tempDirPath);
            return tempDirPath;
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }
}
