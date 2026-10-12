package com.hangeoreum.api.shared.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventProcessor {
    private final OutboxEventRepository repository;
    private final List<OutboxEventHandler> handlers;

    @Transactional
    public void process(UUID id) {
        repository.lockReady(id, Instant.now()).ifPresent(event -> {
            try {
                List<OutboxEventHandler> matching = handlers.stream()
                        .filter(handler -> handler.type().equals(event.getType())).toList();
                if (matching.isEmpty()) {
                    throw new IllegalStateException("No handler for outbox event " + event.getType());
                }
                matching.forEach(handler -> handler.handle(event.getId(), event.getPayload()));
                event.complete();
            } catch (Exception exception) {
                event.fail(exception);
                log.error("Outbox event {} ({}) failed, attempt {}", event.getId(), event.getType(), event.getAttempts(), exception);
            }
        });
    }
}
