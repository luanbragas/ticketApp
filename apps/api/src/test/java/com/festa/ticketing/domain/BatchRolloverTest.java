package com.festa.ticketing.domain;

import com.festa.ticketing.domain.BatchRollover.Slot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.festa.ticketing.domain.BatchStatus.CLOSED;
import static com.festa.ticketing.domain.BatchStatus.ON_SALE;
import static com.festa.ticketing.domain.BatchStatus.SCHEDULED;
import static com.festa.ticketing.domain.BatchStatus.SOLD_OUT;
import static org.assertj.core.api.Assertions.assertThat;

/** Regra de virada (ADR-006): esgotou ou chegou a data, o que vier primeiro; encerrado não reabre. */
class BatchRolloverTest {

	private static final Instant NOW = Instant.parse("2026-10-08T15:00:00Z");
	private static final Instant PAST = NOW.minus(1, ChronoUnit.HOURS);
	private static final Instant FUTURE = NOW.plus(1, ChronoUnit.DAYS);

	@Test
	void firstBatchWithoutDateOpensRightAway() {
		Slot lote1 = slot(0, SCHEDULED, 100, 0, null, null);
		Slot lote2 = slot(1, SCHEDULED, 100, 0, null, null);

		assertThat(BatchRollover.evaluate(List.of(lote2, lote1), NOW)).isEqualTo(Map.of(lote1.id(), ON_SALE));
	}

	@Test
	void turnsWhenSoldOut() {
		Slot lote1 = slot(0, ON_SALE, 100, 100, null, FUTURE);
		Slot lote2 = slot(1, SCHEDULED, 100, 0, null, null);

		assertThat(BatchRollover.evaluate(List.of(lote1, lote2), NOW))
			.containsExactlyInAnyOrderEntriesOf(Map.of(lote1.id(), SOLD_OUT, lote2.id(), ON_SALE));
	}

	@Test
	void turnsWhenTheDateArrivesEvenWithTicketsLeft() {
		Slot lote1 = slot(0, ON_SALE, 100, 40, null, NOW);
		Slot lote2 = slot(1, SCHEDULED, 100, 0, null, null);

		assertThat(BatchRollover.evaluate(List.of(lote1, lote2), NOW))
			.containsExactlyInAnyOrderEntriesOf(Map.of(lote1.id(), CLOSED, lote2.id(), ON_SALE));
	}

	@Test
	void soldOutWinsWhenBothHappen() {
		Slot lote1 = slot(0, ON_SALE, 100, 100, null, PAST);

		assertThat(BatchRollover.evaluate(List.of(lote1), NOW)).isEqualTo(Map.of(lote1.id(), SOLD_OUT));
	}

	@Test
	void reservationsDoNotTurnTheBatch() {
		// 99 pagos e o resto reservado: ainda pode voltar estoque se a reserva expirar.
		Slot lote1 = slot(0, ON_SALE, 100, 99, null, null);
		Slot lote2 = slot(1, SCHEDULED, 100, 0, null, null);

		assertThat(BatchRollover.evaluate(List.of(lote1, lote2), NOW)).isEmpty();
	}

	@Test
	void nextBatchWaitsForItsOpeningAndLaterOnesDoNotJumpAhead() {
		Slot lote1 = slot(0, SOLD_OUT, 100, 100, null, null);
		Slot lote2 = slot(1, SCHEDULED, 100, 0, FUTURE, null);
		Slot lote3 = slot(2, SCHEDULED, 100, 0, null, null);

		assertThat(BatchRollover.evaluate(List.of(lote1, lote2, lote3), NOW)).isEmpty();
		assertThat(BatchRollover.evaluate(List.of(lote1, lote2, lote3), FUTURE)).isEqualTo(Map.of(lote2.id(), ON_SALE));
	}

	@Test
	void batchWhoseTurnPassedBeforeOpeningIsClosedAndSkipped() {
		Slot lote1 = slot(0, SCHEDULED, 100, 0, null, PAST);
		Slot lote2 = slot(1, SCHEDULED, 100, 0, null, null);

		assertThat(BatchRollover.evaluate(List.of(lote1, lote2), NOW))
			.containsExactlyInAnyOrderEntriesOf(Map.of(lote1.id(), CLOSED, lote2.id(), ON_SALE));
	}

	@Test
	void finishedBatchesNeverReopen() {
		Slot esgotado = slot(0, SOLD_OUT, 100, 60, null, FUTURE);
		Slot encerrado = slot(1, CLOSED, 100, 0, null, FUTURE);

		assertThat(BatchRollover.evaluate(List.of(esgotado, encerrado), NOW)).isEmpty();
	}

	@Test
	void runningTwiceChangesNothingTheSecondTime() {
		Slot lote1 = slot(0, ON_SALE, 50, 50, null, null);
		Slot lote2 = slot(1, SCHEDULED, 50, 0, null, null);
		Map<UUID, BatchStatus> first = BatchRollover.evaluate(List.of(lote1, lote2), NOW);

		Slot after1 = slot(lote1.id(), 0, first.get(lote1.id()), 50, 50, null, null);
		Slot after2 = slot(lote2.id(), 1, first.get(lote2.id()), 50, 0, null, null);

		assertThat(BatchRollover.evaluate(List.of(after1, after2), NOW)).isEmpty();
	}

	@Test
	void statusOnlyMovesForward() {
		assertThat(SCHEDULED.canMoveTo(ON_SALE)).isTrue();
		assertThat(ON_SALE.canMoveTo(SOLD_OUT)).isTrue();
		assertThat(SOLD_OUT.canMoveTo(ON_SALE)).isFalse();
		assertThat(CLOSED.canMoveTo(ON_SALE)).isFalse();
		assertThat(ON_SALE.canMoveTo(SCHEDULED)).isFalse();
	}

	private static Slot slot(int position, BatchStatus status, int capacity, int sold, Instant start, Instant end) {
		return slot(UUID.randomUUID(), position, status, capacity, sold, start, end);
	}

	private static Slot slot(UUID id, int position, BatchStatus status, int capacity, int sold, Instant start,
			Instant end) {
		return new Slot(id, status, position, capacity, sold, start, end);
	}

}
