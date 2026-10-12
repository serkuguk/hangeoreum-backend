package com.hangeoreum.api.shared.events;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OutboxDispatcher {
    private final OutboxEventRepository repository;
    private final OutboxEventProcessor processor;

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:5000}")
    public void dispatch() {
        repository.readyIds(Instant.now()).forEach(processor::process);
    }
}
