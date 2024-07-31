package com.mikemybytes.jfr.pinning;

import jdk.jfr.consumer.RecordingStream;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
class JfrEventLifecycle implements SmartLifecycle {
    private static final String JFR_EVENT_KEY = "jdk.VirtualThreadPinned";
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final JfrVirtualThreadPinnedEventHandler virtualThreadPinnedEventHandler;

    private RecordingStream recordingStream;

    JfrEventLifecycle(JfrVirtualThreadPinnedEventHandler virtualThreadPinnedEventHandler) {
        this.virtualThreadPinnedEventHandler = virtualThreadPinnedEventHandler;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            recordingStream = new RecordingStream();
            recordingStream.enable(JFR_EVENT_KEY)
                    .withStackTrace()
                    .withThreshold(Duration.ofMillis(1));
            recordingStream.onEvent(JFR_EVENT_KEY, virtualThreadPinnedEventHandler::handle);
            recordingStream.setReuse(true);
            recordingStream.setMaxAge(Duration.ofSeconds(5));
            recordingStream.startAsync();

            virtualThreadPinnedEventHandler.initializePrintDetailsThread();
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            recordingStream.close();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return 0;
    }
}