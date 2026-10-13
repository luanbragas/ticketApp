package com.festa.payment.web;

import com.festa.TestBrowser;
import com.festa.TestCpf;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
import com.festa.TestcontainersConfiguration;
import com.festa.order.app.OrderExpiryService;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pronto quando do M5 (com o gateway simulado): compra PIX e cartão viram pedido pago pela conciliação do
 * webhook; caso de borda de pagamento depois da expiração; conexão da conta do produtor por OAuth.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	OutboxWorker outbox;

	@Autowired
	OrderExpiryService expiry;

	@Test
	void pixPaymentBecomesPaidOrderAndTickets() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		Order order = place(shop, 2);

		String first = JsonPath.read(pay(order, "{\"method\":\"PIX\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.amountCents").value(6600))
			.andExpect(jsonPath("$.pixQrCode").value(notNullValue()))
			.andExpect(jsonPath("$.simulated").value(true))
			.andReturn().getResponse().getContentAsString(), "$.id");
		// Clicar de novo devolve o mesmo PIX.
		pay(order, "{\"method\":\"PIX\"}").andExpect(jsonPath("$.id").value(first));
		assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");

		simulateApproval(order).andExpect(status().isNoContent());
		outbox.drain(Integer.MAX_VALUE);

		assertThat(orderStatus(order)).isEqualTo("PAID");
		current(order).andExpect(jsonPath("$.status").value("APPROVED"));
		assertThat(jdbc.sql("SELECT count(*) FROM tickets WHERE order_id = ?").param(order.id())
			.query(Integer.class).single()).isEqualTo(2);
		assertThat(jdbc.sql("SELECT platform_fee_cents FROM payments WHERE order_id = ?").param(order.id())
			.query(Long.class).single()).isEqualTo(600);
		// Repetir a aprovação (webhook repetido) não muda nada.
		simulateApproval(order).andExpect(status().isNoContent());
		pay(order, "{\"method\":\"PIX\"}").andExpect(status().isConflict());
		pay(order, "{\"method\":\"CARD\",\"cardToken\":\"tok\",\"paymentMethodId\":\"visa\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/order-not-payable"));
	}

	@Test
	void declinedCardCanBeRetriedAndApprovedCardPays() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		Order order = place(shop, 1);

		pay(order, "{\"method\":\"CARD\",\"cardToken\":\"fake-reject\",\"paymentMethodId\":\"visa\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REJECTED"));
		assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");

		pay(order, "{\"method\":\"CARD\",\"cardToken\":\"tok-ok\",\"paymentMethodId\":\"visa\"}")
			.andExpect(jsonPath("$.status").value("APPROVED"));
		assertThat(orderStatus(order)).isEqualTo("PAID");
	}

	@Test
	void latePaymentWithStockLeftStillGetsTheTickets() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		Order order = place(shop, 1);
		pay(order, "{\"method\":\"PIX\"}").andExpect(status().isCreated());
		expire(order);
		assertThat(orderStatus(order)).isEqualTo("EXPIRED");

		simulateApproval(order).andExpect(status().isNoContent());

		assertThat(orderStatus(order)).isEqualTo("PAID");
		assertThat(jdbc.sql("SELECT sold FROM ticket_batches WHERE id = ?").param(UUID.fromString(shop.pista()))
			.query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void latePaymentWithoutStockIsRefunded() throws Exception {
		Shop shop = TestShop.open(mvc, 1, null);
		Order late = place(shop, 1);
		pay(late, "{\"method\":\"PIX\"}").andExpect(status().isCreated());
		expire(late);
		Order other = place(shop, 1);

		simulateApproval(late).andExpect(status().isNoContent());

		assertThat(orderStatus(late)).isEqualTo("REFUNDED");
		current(late).andExpect(jsonPath("$.status").value("REFUNDED"));
		assertThat(orderStatus(other)).isEqualTo("PENDING_PAYMENT");
		assertThat(jdbc.sql("SELECT reserved FROM ticket_batches WHERE id = ?").param(UUID.fromString(shop.pista()))
			.query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void onlyTheKeyHolderPays() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		Order order = place(shop, 1);

		new TestBrowser(mvc).send(MockMvcRequestBuilders.post("/api/v1/public/orders/" + order.id() + "/payment")
				.header("X-Order-Key", UUID.randomUUID().toString().replace("-", "") + "x"), "{\"method\":\"PIX\"}")
			.andExpect(status().isNotFound());
	}

	@Test
	void webhookNeedsAValidSignature() throws Exception {
		TestBrowser gateway = new TestBrowser(mvc);
		String body = "{\"type\":\"payment\",\"data\":{\"id\":\"123\"}}";

		gateway.send(MockMvcRequestBuilders.post("/api/v1/webhooks/mercadopago?data.id=123&type=payment")
				.header("x-signature", "ts=1,v1=00").header("x-request-id", "r1"), body)
			.andExpect(status().isUnauthorized());

		String v1 = hmac("id:123;request-id:r1;ts:1;");
		gateway.send(MockMvcRequestBuilders.post("/api/v1/webhooks/mercadopago?data.id=123&type=payment")
				.header("x-signature", "ts=1,v1=" + v1).header("x-request-id", "r1"), body)
			.andExpect(status().isOk());
	}

	@Test
	void ownerConnectsAccountWithOneTimeState() throws Exception {
		Shop shop = TestShop.open(mvc, 10, null);
		String base = "/api/v1/orgs/" + shop.org() + "/payment-account";

		shop.producer().get(base).andExpect(jsonPath("$.connected").value(true));
		String url = JsonPath.read(shop.producer().post(base + "/connect", "{}").andReturn().getResponse()
			.getContentAsString(), "$.authorizationUrl");
		var params = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
		String callback = "{\"code\":\"" + params.getFirst("code") + "\",\"state\":\"" + params.getFirst("state") + "\"}";

		shop.producer().post(base + "/callback", callback).andExpect(status().isOk());
		shop.producer().post(base + "/callback", callback)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-oauth-state"));
		shop.producer().post(base + "/callback", "{\"code\":\"fake-x\",\"state\":\"inventado\"}")
			.andExpect(status().isBadRequest());
		assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = 'payment-account.connected' AND organization_id = ?")
			.param(UUID.fromString(shop.org())).query(Integer.class).single()).isEqualTo(2);
		// Token do vendedor nunca em claro no banco.
		assertThat(jdbc.sql("SELECT count(*) FROM payment_accounts WHERE organization_id = ? AND position('fake-access' IN encode(access_token_encrypted, 'escape')) > 0")
			.param(UUID.fromString(shop.org())).query(Integer.class).single()).isZero();
	}

	// ---------- apoio ----------

	private record Order(UUID id, String key) {
	}

	private Order place(Shop shop, int tickets) throws Exception {
		String key = UUID.randomUUID().toString().replace("-", "") + "k";
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < tickets; i++) {
			items.append(i == 0 ? "" : ",").append("{\"batchId\":\"").append(shop.pista())
				.append("\",\"holderName\":\"Titular\",\"holderCpf\":\"").append(TestCpf.random()).append("\"}");
		}
		String json = new TestBrowser(mvc).send(MockMvcRequestBuilders.post("/api/v1/public/orders")
				.header("Idempotency-Key", key), """
				{"event":"%s","buyer":{"name":"Ana","email":"ana@festa.test","cpf":"%s"},"tickets":[%s],
				 "adultDeclared":true,"termsAccepted":true}""".formatted(shop.slug(), TestCpf.random(), items))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return new Order(UUID.fromString(JsonPath.read(json, "$.id")), key);
	}

	private ResultActions pay(Order order, String body) throws Exception {
		return new TestBrowser(mvc).send(MockMvcRequestBuilders.post("/api/v1/public/orders/" + order.id() + "/payment")
				.header("X-Order-Key", order.key()), body);
	}

	private ResultActions current(Order order) throws Exception {
		return new TestBrowser(mvc).send(MockMvcRequestBuilders.get("/api/v1/public/orders/" + order.id() + "/payment")
				.header("X-Order-Key", order.key()), "");
	}

	private ResultActions simulateApproval(Order order) throws Exception {
		return new TestBrowser(mvc).send(MockMvcRequestBuilders
			.post("/api/v1/public/orders/" + order.id() + "/payment/simulate-approval")
			.header("X-Order-Key", order.key()), "");
	}

	private void expire(Order order) {
		jdbc.sql("UPDATE orders SET expires_at = now() - interval '1 minute' WHERE id = ?").param(order.id()).update();
		expiry.expireDue();
	}

	private String orderStatus(Order order) {
		return jdbc.sql("SELECT status FROM orders WHERE id = ?").param(order.id()).query(String.class).single();
	}

	private static String hmac(String manifest) throws Exception {
		javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
		mac.init(new javax.crypto.spec.SecretKeySpec("segredo-de-teste".getBytes(), "HmacSHA256"));
		return java.util.HexFormat.of().formatHex(mac.doFinal(manifest.getBytes()));
	}

}
