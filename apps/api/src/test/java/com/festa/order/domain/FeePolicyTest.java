package com.festa.order.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Taxa paga pelo comprador por cima do preço (ADR-007), sempre em centavos inteiros. */
class FeePolicyTest {

	@Test
	void tenPercentRoundedToTheNearestCent() {
		FeePolicy fees = new FeePolicy(1000, 0);

		assertThat(fees.feeFor(3000)).isEqualTo(300);
		assertThat(fees.feeFor(4590)).isEqualTo(459);
		assertThat(fees.feeFor(1995)).isEqualTo(200); // 199,5 → 200
		assertThat(fees.feeFor(1994)).isEqualTo(199); // 199,4 → 199
	}

	@Test
	void minimumAppliesToCheapTickets() {
		FeePolicy fees = new FeePolicy(1000, 300);

		assertThat(fees.feeFor(1000)).isEqualTo(300);
		assertThat(fees.feeFor(5000)).isEqualTo(500);
	}

	@Test
	void zeroMeansNoFee() {
		assertThat(new FeePolicy(0, 0).feeFor(3000)).isZero();
	}

	@Test
	void rejectsAbsurdValues() {
		assertThatThrownBy(() -> new FeePolicy(-1, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new FeePolicy(6000, 0)).isInstanceOf(IllegalArgumentException.class);
	}

}
