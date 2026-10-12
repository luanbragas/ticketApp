package com.festa.payment.web;

import com.festa.TestBrowser;
import com.festa.TestOrders;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
import com.festa.TestcontainersConfiguration;
import com.festa.payment.app.FakePaymentGateway;
import com.festa.payment.app.PaymentService;
import com.festa.shared.outbox.OutboxWorker;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLAN.md M9, reembolso manual: estorna no gateway, cancela os ingressos, devolve o estoque, tira a venda do
 * promoter e fica no audit log. Estorno e chargeback feitos no gateway seguem o mesmo caminho pelo webhook.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefundFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	OutboxWorker outbox;

	@Autowired
	FakePaymentGateway gateway;

	@Autowired
	PaymentService payments;

	@Test
	void ownerRefundsPaidOrder() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String code = promoter(shop);
		UUID order = paidOrder(shop, 2, code);
		assertThat(sold(shop)).isEqualTo(2);

		refund(shop.producer(), shop, order)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amountCents").value(6600));
		outbox.drain(100);

		assertThat(orderStatus(order)).isEqualTo("REFUNDED");
		assertThat(jdbc.sql("SELECT status FROM payments WHERE order_id = ? AND status <> 'PENDING'").param(order)
			.query(String.class).list()).containsExactly("REFUNDED");
		assertThat(ticketStatuses(order)).containsOnly("CANCELLED").hasSize(2);
		assertThat(sold(shop)).isZero();
		assertThat(jdbc.sql("SELECT kind || ':' || amount_cents || ':' || reason || ':' || (created_by IS NOT NULL) FROM refunds WHERE order_id = ?")
			.param(order).query(String.class).single()).isEqualTo("REFUND:6600:Festa adiada:true");
		assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = 'order.refunded' AND entity_id = ?")
			.param(order).query(Integer.class).single()).isEqualTo(1);
		shop.producer().get(shop.eventPath() + "/promoters").andExpect(jsonPath("$.links[0].tickets").value(0));

		refund(shop.producer(), shop, order)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/order-not-refundable"));
	}

	@Test
	void ticketThatAlreadyEnteredBlocksRefund() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		UUID order = paidOrder(shop, 2, null);
		jdbc.sql("UPDATE tickets SET status = 'CHECKED_IN' WHERE id = (SELECT id FROM tickets WHERE order_id = ? LIMIT 1)")
			.param(order).update();

		refund(shop.producer(), shop, order)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/tickets-already-used"));
		assertThat(orderStatus(order)).isEqualTo("PAID");
	}

	@Test
	void onlyOwnerOrAdminOfTheOrganizationRefunds() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		Shop other = TestShop.open(mvc, 100, null);
		UUID order = paidOrder(shop, 1, null);

		// Outra organização: nem pelo caminho dela, nem pelo da dona do pedido.
		other.producer().post("/api/v1/orgs/" + other.org() + "/orders/" + order + "/refund", "{\"reason\":\"x\"}")
			.andExpect(status().isNotFound());
		refund(other.producer(), shop, order).andExpect(status().isNotFound());
		refund(shop.producer(), shop, order, "{\"reason\":\"  \"}").andExpect(status().isBadRequest());
		assertThat(orderStatus(order)).isEqualTo("PAID");
	}

	@Test
	void chargebackFromGatewayCancelsTickets() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		UUID order = paidOrder(shop, 1, null);
		String providerId = jdbc.sql("SELECT provider_payment_id FROM payments WHERE order_id = ? AND status = 'APPROVED'")
			.param(order).query(String.class).single();

		gateway.chargeback(providerId);
		payments.reconcile(providerId);
		payments.reconcile(providerId);
		outbox.drain(100);

		assertThat(orderStatus(order)).isEqualTo("CHARGEBACK");
		assertThat(ticketStatuses(order)).containsExactly("CANCELLED");
		assertThat(jdbc.sql("SELECT kind || ':' || (created_by IS NULL) FROM refunds WHERE order_id = ?").param(order)
			.query(String.class).single()).isEqualTo("CHARGEBACK:true");
		assertThat(sold(shop)).isZero();
	}

	@Test
	void refundThenChargebackKeepsStockRight() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		UUID order = paidOrder(shop, 1, null);
		String providerId = jdbc.sql("SELECT provider_payment_id FROM payments WHERE order_id = ? AND status = 'APPROVED'")
			.param(order).query(String.class).single();
		refund(shop.producer(), shop, order).andExpect(status().isOk());

		gateway.chargeback(providerId);
		payments.reconcile(providerId);
		outbox.drain(100);

		assertThat(orderStatus(order)).isEqualTo("CHARGEBACK");
		assertThat(sold(shop)).isZero();
	}

	// ---------- apoio ----------

	private ResultActions refund(TestBrowser who, Shop shop, UUID order) throws Exception {
		return refund(who, shop, order, "{\"reason\":\"Festa adiada\"}");
	}

	private ResultActions refund(TestBrowser who, Shop shop, UUID order, String body) throws Exception {
		return who.post("/api/v1/orgs/" + shop.org() + "/orders/" + order + "/refund", body);
	}

	private String promoter(Shop shop) throws Exception {
		return JsonPath.read(shop.producer().post(shop.eventPath() + "/promoters", "{\"name\":\"Caio\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.links[0].code");
	}

	private UUID paidOrder(Shop shop, int tickets, String promoterCode) throws Exception {
		UUID order = TestOrders.paid(mvc, outbox, shop, tickets, promoterCode);
		assertThat(orderStatus(order)).isEqualTo("PAID");
		return order;
	}

	private String orderStatus(UUID order) {
		return jdbc.sql("SELECT status FROM orders WHERE id = ?").param(order).query(String.class).single();
	}

	private List<String> ticketStatuses(UUID order) {
		return jdbc.sql("SELECT status FROM tickets WHERE order_id = ?").param(order).query(String.class).list();
	}

	private int sold(Shop shop) {
		return jdbc.sql("SELECT sold FROM ticket_batches WHERE id = ?").param(UUID.fromString(shop.pista()))
			.query(Integer.class).single();
	}

}
