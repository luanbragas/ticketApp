package com.festa.finance.web;

import com.festa.TestOrders;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
import com.festa.TestcontainersConfiguration;
import com.festa.shared.outbox.OutboxWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PLAN.md M9, financeiro básico: vendas, estornos, líquido do produtor e taxa da plataforma, por evento. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinanceFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	OutboxWorker outbox;

	@Test
	void salesMinusRefundsIsTheProducersNet() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		TestOrders.paid(mvc, outbox, shop, 2, null);
		UUID refunded = TestOrders.paid(mvc, outbox, shop, 1, null);
		shop.producer().post("/api/v1/orgs/" + shop.org() + "/orders/" + refunded + "/refund", "{\"reason\":\"Duplicado\"}")
			.andExpect(status().isOk());
		outbox.drain(Integer.MAX_VALUE);
		// Entregar de novo não lança duas vezes.
		jdbc.sql("UPDATE outbox_events SET status = 'PENDING', processed_at = NULL, next_attempt_at = now() - interval '1 minute' WHERE aggregate_id = ?")
			.param(refunded).update();
		outbox.drain(Integer.MAX_VALUE);

		shop.producer().get(shop.eventPath() + "/finance")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totals.ticketSalesCents").value(9000))
			.andExpect(jsonPath("$.totals.ticketRefundsCents").value(3000))
			.andExpect(jsonPath("$.totals.netCents").value(6000))
			.andExpect(jsonPath("$.totals.ticketsSold").value(3))
			.andExpect(jsonPath("$.totals.ticketsRefunded").value(1))
			.andExpect(jsonPath("$.totals.paidOrders").value(2))
			.andExpect(jsonPath("$.totals.refundedOrders").value(1))
			.andExpect(jsonPath("$.totals.serviceFeeCents").value(900))
			.andExpect(jsonPath("$.totals.serviceFeeRefundsCents").value(300));

		shop.producer().get("/api/v1/orgs/" + shop.org() + "/finance")
			.andExpect(jsonPath("$.events.length()").value(1))
			.andExpect(jsonPath("$.events[0].eventId").value(shop.eventId()))
			.andExpect(jsonPath("$.totals.netCents").value(6000));
	}

	@Test
	void otherOrganizationSeesNothing() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		Shop other = TestShop.open(mvc, 100, null);
		TestOrders.paid(mvc, outbox, shop, 1, null);

		other.producer().get(shop.eventPath() + "/finance").andExpect(status().isNotFound());
		other.producer().get("/api/v1/orgs/" + other.org() + "/events/" + shop.eventId() + "/finance")
			.andExpect(status().isNotFound());
		other.producer().get("/api/v1/orgs/" + other.org() + "/finance")
			.andExpect(jsonPath("$.events.length()").value(0))
			.andExpect(jsonPath("$.totals.ticketSalesCents").value(0));
	}

}
