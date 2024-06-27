package com.mikemybytes.jfr.pinning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/pinning")
class ThreadPinningController {

    @PostMapping
    void pinCarrierThread()  {
        Thread.ofVirtual()
                .start(() -> {
                    synchronized (this) {
                        sleep(5);
                    }
                });
    }

    private void sleep(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
