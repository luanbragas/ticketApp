package com.festa.order.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

	private static final Instant NOW = Instant.parse("2026-11-01T15:00:00Z");

	@Test
	void startsPendingWithTotalAsSubtotalPlusFee() {
		Order order = order();

		assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
		assertThat(order.getTotalCents()).isEqualTo(6600);
		assertThat(order.getBuyerEmail()).isEqualTo("ana@festa.test");
	}

	@Test
	void expiresOnlyAfterItsTimeAndOnlyOnce() {
		Order order = order();

		assertThatThrownBy(() -> order.expire(NOW.plusSeconds(60))).isInstanceOf(IllegalStateException.class);
		order.expire(NOW.plus(Duration.ofMinutes(10)));
		assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
		assertThatThrownBy(() -> order.expire(NOW.plus(Duration.ofMinutes(11))))
			.isInstanceOfSatisfying(OrderRuleException.class, ex -> assertThat(ex.isConflict()).isTrue());
	}

	private static Order order() {
		return new Order(UUID.randomUUID(), UUID.randomUUID(),
				new Order.Buyer("Ana", " Ana@Festa.test ", null, new byte[] { 1 }, "hash"), 6000, 600, NOW,
				NOW.plus(Duration.ofMinutes(10)), "key-hash", true, "rascunho");
	}

}
