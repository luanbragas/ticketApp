package com.festa.shared.outbox;

import java.util.UUID;

/** Evento lido do outbox; {@code payload} é o JSON gravado por quem publicou. */
public record OutboxMessage(UUID id, String type, UUID aggregateId, String payload, int attempts) {
}
