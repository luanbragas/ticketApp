package com.festa.ticketing.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketBatchTest {

	private static final Instant START = Instant.parse("2026-10-10T15:00:00Z");

	private final TicketType pista = new TicketType(UUID.randomUUID(), UUID.randomUUID(), "Pista", null, false, 0);

	@Test
	void startsScheduledWithDefaultLimitPerOrder() {
		TicketBatch batch = new TicketBatch(pista, terms(3000, 100, null, null), 0);

		assertThat(batch.getStatus()).isEqualTo(BatchStatus.SCHEDULED);
		assertThat(batch.getEffectiveMaxPerOrder()).isEqualTo(TicketBatch.DEFAULT_MAX_PER_ORDER);
		assertThat(batch.getEventId()).isEqualTo(pista.getEventId());
		assertThat(batch.getRemaining()).isEqualTo(100);
	}

	@Test
	void turnMustComeAfterOpening() {
		assertThatThrownBy(() -> new TicketBatch(pista, terms(3000, 100, START, START), 0))
			.isInstanceOfSatisfying(TicketRuleException.class,
					ex -> assertThat(ex.getCode()).isEqualTo("invalid-sales-window"));
	}

	@Test
	void rejectsFreeOrNegativePrice() {
		assertThatThrownBy(() -> new TicketBatch(pista, terms(0, 100, null, null), 0))
			.isInstanceOfSatisfying(TicketRuleException.class, ex -> assertThat(ex.getCode()).isEqualTo("invalid-price"));
	}

	@Test
	void closedBatchCannotBeEditedOrClosedAgain() {
		TicketBatch batch = new TicketBatch(pista, terms(3000, 100, null, null), 0);
		batch.close();

		assertThatThrownBy(() -> batch.update(terms(2000, 100, null, null)))
			.isInstanceOfSatisfying(TicketRuleException.class, ex -> assertThat(ex.isConflict()).isTrue());
		assertThatThrownBy(batch::close).isInstanceOf(TicketRuleException.class);
	}

	@Test
	void rolloverCannotMoveBackwards() {
		TicketBatch batch = new TicketBatch(pista, terms(3000, 100, null, null), 0);
		batch.moveTo(BatchStatus.ON_SALE);
		batch.moveTo(BatchStatus.SOLD_OUT);

		assertThatThrownBy(() -> batch.moveTo(BatchStatus.ON_SALE)).isInstanceOf(IllegalStateException.class);
	}

	private static TicketBatch.Terms terms(long price, int capacity, Instant start, Instant end) {
		return new TicketBatch.Terms("Lote 1", price, capacity, start, end, null, true);
	}

}
