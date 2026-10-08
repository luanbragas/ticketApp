package com.festa.ticketing.domain;

import com.festa.ticketing.domain.HalfPriceQuota.Line;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cota de meia (Lei 12.933/2013): pelo menos 40% dos ingressos à venda, por padrão. */
class HalfPriceQuotaTest {

	@Test
	void fortyPercentOfTheTotalIncludingHalfPrice() {
		HalfPriceQuota quota = HalfPriceQuota.of(40, List.of(
				new Line(false, BatchStatus.ON_SALE, 300, 0, 0),
				new Line(true, BatchStatus.ON_SALE, 200, 0, 0)));

		assertThat(quota.total()).isEqualTo(500);
		assertThat(quota.halfPrice()).isEqualTo(200);
		assertThat(quota.minimum()).isEqualTo(200);
		assertThat(quota.met()).isTrue();
	}

	@Test
	void missesWhenHalfPriceIsShort() {
		HalfPriceQuota quota = HalfPriceQuota.of(40, List.of(
				new Line(false, BatchStatus.SCHEDULED, 400, 0, 0),
				new Line(true, BatchStatus.SCHEDULED, 100, 0, 0)));

		assertThat(quota.minimum()).isEqualTo(200);
		assertThat(quota.met()).isFalse();
	}

	@Test
	void roundsTheMinimumUp() {
		HalfPriceQuota quota = HalfPriceQuota.of(40, List.of(new Line(false, BatchStatus.ON_SALE, 101, 0, 0)));

		// 40% de 101 = 40,4 → 41.
		assertThat(quota.minimum()).isEqualTo(41);
	}

	@Test
	void closedBatchesOnlyCountWhatWasActuallyOffered() {
		HalfPriceQuota quota = HalfPriceQuota.of(40, List.of(
				new Line(false, BatchStatus.CLOSED, 300, 120, 0),
				new Line(false, BatchStatus.SOLD_OUT, 100, 100, 0),
				new Line(true, BatchStatus.CLOSED, 200, 70, 10)));

		assertThat(quota.total()).isEqualTo(300);
		assertThat(quota.halfPrice()).isEqualTo(80);
	}

	@Test
	void noTicketsMeansNothingToMeet() {
		HalfPriceQuota quota = HalfPriceQuota.of(40, List.of());

		assertThat(quota.minimum()).isZero();
		assertThat(quota.met()).isTrue();
	}

}
