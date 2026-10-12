package com.hangeoreum.api.shared.events;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public UUID publish(String type, Object payload) {
        UUID id = UUID.randomUUID();
        try {
            repository.save(OutboxEvent.pending(id, type, objectMapper.writeValueAsString(payload)));
            return id;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot persist outbox event " + type, exception);
        }
    }
}
