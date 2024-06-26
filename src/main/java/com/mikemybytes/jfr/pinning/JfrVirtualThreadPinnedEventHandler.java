package com.mikemybytes.jfr.pinning;

import io.micrometer.core.instrument.MeterRegistry;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
class JfrVirtualThreadPinnedEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(JfrVirtualThreadPinnedEventHandler.class);
    private static final int STACK_TRACE_MAX_DEPTH = 25;

    private final AtomicLong pinnedEventsCounter = new AtomicLong(0);
    private List<String> pinnedDetails = new ArrayList<>();
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    void initializePrintDetailsThread() {
        String filename = "pinned-thread-details";
        Path tempFolder = NativeMemoryTracker.initializeAndGetTempFolder();
        logger.info(
                "Starting the thread to keep a status of pinned thread event. Will print details every minute. A part of the stacktraces details can be found in the file : {}/{}",
                tempFolder, filename);
        executorService.scheduleAtFixedRate(() -> {
            logger.info("Pinned events count: {}.",
                    pinnedEventsCounter.get());
            if (!pinnedDetails.isEmpty()) {
                List<String> copy = List.copyOf(pinnedDetails);
                pinnedDetails.clear();
                try {
                    Path filePath = tempFolder.resolve(filename);
                    Files.write(filePath, copy, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    logger.error("Error writing to file", e);
                }
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    void handle(RecordedEvent event) {
        pinnedEventsCounter.incrementAndGet();
        // marked as nullable in Javadoc
        var thread = event.getThread() != null ? event.getThread().getJavaName() : "<unknown>";
        var duration = event.getDuration();
        var startTime = LocalDateTime.ofInstant(event.getStartTime(), ZoneId.systemDefault());
        var stackTrace = formatStackTrace(event.getStackTrace(), STACK_TRACE_MAX_DEPTH);

        if (ThreadLocalRandom.current().nextInt(100) == 0) {
            String eventDetails = String.format(
                    "Thread '%s' pinned for: %sms at %s, stacktrace: \n%s",
                    thread,
                    duration.toMillis(),
                    startTime,
                    stackTrace
            );
            pinnedDetails.add(eventDetails);
        }
    }

    private String formatStackTrace(RecordedStackTrace stackTrace, int maxDepth) {
        if (stackTrace == null) {
            return "\t<not available>";
        }
        String formatted = "\t" + stackTrace.getFrames().stream()
                .limit(maxDepth)
                .map(JfrVirtualThreadPinnedEventHandler::formatStackTraceFrame)
                .collect(Collectors.joining("\n\t"));
        if (maxDepth < stackTrace.getFrames().size()) {
            return formatted + "\n\t(...)"; // truncated
        }
        return formatted;
    }

    private static String formatStackTraceFrame(RecordedFrame frame) {
        return frame.getMethod().getType().getName() + "#" + frame.getMethod().getName() + ": " + frame.getLineNumber();
    }
}
