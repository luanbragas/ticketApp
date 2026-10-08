package com.festa.order.web;

import com.festa.TestBrowser;
import com.festa.TestCpf;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
import com.festa.TestcontainersConfiguration;
import com.festa.order.app.OrderExpiryService;
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Seleção, pedido e reserva pela página pública (PLAN.md M4). Taxa padrão: 10%. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CheckoutFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	OrderExpiryService expiry;

	@Test
	void quoteComputesSubtotalFeeAndTotalOnTheServer() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		TestBrowser buyer = new TestBrowser(mvc);

		buyer.post("/api/v1/public/events/" + shop.slug() + "/quote", """
				{"items":[{"batchId":"%s","quantity":2},{"batchId":"%s","quantity":1}]}""".formatted(shop.pista(), shop.meia()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lines", hasSize(2)))
			.andExpect(jsonPath("$.lines[0].typeName").value("Pista"))
			.andExpect(jsonPath("$.lines[0].unitFeeCents").value(300))
			.andExpect(jsonPath("$.lines[0].totalCents").value(6600))
			.andExpect(jsonPath("$.subtotalCents").value(7500))
			.andExpect(jsonPath("$.feeCents").value(750))
			.andExpect(jsonPath("$.totalCents").value(8250));

		// Cotar não reserva nada.
		assertThat(reserved(shop.pista())).isZero();
	}

	@Test
	void placesPendingOrderReservesStockAndNeverStoresPlainCpf() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String cpf = TestCpf.random();
		String key = key();

		String json = place(shop, key, cpf, ticket(shop.pista(), cpf, null) + "," + ticket(shop.meia(), TestCpf.random(), "STUDENT"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.subtotalCents").value(4500))
			.andExpect(jsonPath("$.feeCents").value(450))
			.andExpect(jsonPath("$.totalCents").value(4950))
			.andExpect(jsonPath("$.buyer.cpf").value("***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**"))
			.andExpect(jsonPath("$.items[1].halfPrice").value(true))
			.andExpect(jsonPath("$.items[1].halfPriceReason").value("STUDENT"))
			.andExpect(jsonPath("$.items[0].typeName").value("Pista"))
			.andReturn().getResponse().getContentAsString();
		String orderId = JsonPath.read(json, "$.id");

		assertThat(reserved(shop.pista())).isEqualTo(1);
		assertThat(reserved(shop.meia())).isEqualTo(1);
		byte[] stored = jdbc.sql("SELECT buyer_cpf_encrypted FROM orders WHERE id = ?")
			.param(UUID.fromString(orderId)).query(byte[].class).single();
		assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(cpf);
		String keyColumn = jdbc.sql("SELECT access_key_hash FROM orders WHERE id = ?")
			.param(UUID.fromString(orderId)).query(String.class).single();
		assertThat(keyColumn).isNotEqualTo(key);
	}

	@Test
	void sameKeyReturnsTheSameOrderWithoutReservingTwice() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String cpf = TestCpf.random();
		String key = key();

		String first = JsonPath.read(place(shop, key, cpf, ticket(shop.pista(), cpf, null))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		place(shop, key, cpf, ticket(shop.pista(), cpf, null))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(first));

		assertThat(reserved(shop.pista())).isEqualTo(1);
	}

	@Test
	void onlyTheKeyHolderSeesTheOrder() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String cpf = TestCpf.random();
		String key = key();
		String id = JsonPath.read(place(shop, key, cpf, ticket(shop.pista(), cpf, null))
			.andReturn().getResponse().getContentAsString(), "$.id");
		TestBrowser other = new TestBrowser(mvc);

		other.send(MockMvcRequestBuilders.get("/api/v1/public/orders/" + id).header("X-Order-Key", key), "")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
		other.send(MockMvcRequestBuilders.get("/api/v1/public/orders/" + id).header("X-Order-Key", key()), "")
			.andExpect(status().isNotFound());
		other.get("/api/v1/public/orders/" + id).andExpect(status().isBadRequest());
	}

	@Test
	void whenOneBatchHasNoStockNothingIsReserved() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		jdbc.sql("UPDATE ticket_batches SET sold = 49 WHERE id = ?").param(UUID.fromString(shop.meia())).update();
		String cpf = TestCpf.random();

		place(shop, key(), cpf, ticket(shop.pista(), cpf, null) + "," + ticket(shop.meia(), TestCpf.random(), "PCD")
				+ "," + ticket(shop.meia(), TestCpf.random(), "PCD"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/batch-unavailable"))
			.andExpect(jsonPath("$.batchId").value(shop.meia()));

		assertThat(reserved(shop.pista())).isZero();
		assertThat(reserved(shop.meia())).isZero();
	}

	@Test
	void cpfLimitCountsPendingAndPaidOrders() throws Exception {
		Shop shop = TestShop.open(mvc, 100, 2);
		String cpf = TestCpf.random();

		place(shop, key(), cpf, ticket(shop.pista(), cpf, null) + "," + ticket(shop.pista(), TestCpf.random(), null))
			.andExpect(status().isCreated());
		place(shop, key(), cpf, ticket(shop.pista(), cpf, null))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/cpf-limit"))
			.andExpect(jsonPath("$.remaining").value(0));

		// Outro comprador continua podendo.
		String other = TestCpf.random();
		place(shop, key(), other, ticket(shop.pista(), other, null)).andExpect(status().isCreated());
	}

	@Test
	void halfPriceNeedsTheBenefitAndAdultsOnlyNeedsTheDeclaration() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String cpf = TestCpf.random();

		place(shop, key(), cpf, ticket(shop.meia(), cpf, null))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/half-price-reason-required"));

		new TestBrowser(mvc).send(MockMvcRequestBuilders.post("/api/v1/public/orders").header("Idempotency-Key", key()),
				body(shop, cpf, ticket(shop.pista(), cpf, null), false))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/adult-declaration-required"));
	}

	@Test
	void rejectsInvalidCpfAndForeignBatch() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		Shop other = TestShop.open(mvc, 100, null);
		String cpf = TestCpf.random();

		place(shop, key(), "111.111.111-11", ticket(shop.pista(), cpf, null))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-cpf"))
			.andExpect(jsonPath("$.field").value("buyer.cpf"));
		place(shop, key(), cpf, ticket(other.pista(), cpf, null))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/unknown-batch"));
		assertThat(reserved(other.pista())).isZero();
	}

	@Test
	void respectsMaxPerOrderAndClosedSales() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		TestBrowser buyer = new TestBrowser(mvc);

		buyer.post("/api/v1/public/events/" + shop.slug() + "/quote",
				"{\"items\":[{\"batchId\":\"" + shop.pista() + "\",\"quantity\":11}]}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/max-per-order"))
			.andExpect(jsonPath("$.max").value(10));

		shop.producer().post(shop.eventPath() + "/end", "{}").andExpect(status().isOk());
		buyer.post("/api/v1/public/events/" + shop.slug() + "/quote",
				"{\"items\":[{\"batchId\":\"" + shop.pista() + "\",\"quantity\":1}]}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/sales-closed"));
	}

	@Test
	void expiredOrderGivesTheStockBack() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String cpf = TestCpf.random();
		String key = key();
		String id = JsonPath.read(place(shop, key, cpf, ticket(shop.pista(), cpf, null) + "," + ticket(shop.pista(), cpf, null))
			.andReturn().getResponse().getContentAsString(), "$.id");
		assertThat(reserved(shop.pista())).isEqualTo(2);

		jdbc.sql("UPDATE orders SET expires_at = now() - interval '1 second' WHERE id = ?").param(UUID.fromString(id)).update();
		assertThat(expiry.expireDue()).isGreaterThanOrEqualTo(1);

		assertThat(reserved(shop.pista())).isZero();
		new TestBrowser(mvc).send(MockMvcRequestBuilders.get("/api/v1/public/orders/" + id).header("X-Order-Key", key), "")
			.andExpect(jsonPath("$.status").value("EXPIRED"));
		// Rodar de novo não devolve estoque duas vezes.
		expiry.expireDue();
		assertThat(reserved(shop.pista())).isZero();
	}

	// ---------- apoio ----------

	private ResultActions place(Shop shop, String key, String buyerCpf, String tickets) throws Exception {
		return new TestBrowser(mvc).send(
				MockMvcRequestBuilders.post("/api/v1/public/orders").header("Idempotency-Key", key),
				body(shop, buyerCpf, tickets, true));
	}

	private static String body(Shop shop, String buyerCpf, String tickets, boolean adult) {
		return """
				{"event":"%s","buyer":{"name":"Ana Souza","email":"ana@festa.test","phone":"(31) 99999-0000","cpf":"%s"},
				 "tickets":[%s],"adultDeclared":%s,"termsAccepted":true}""".formatted(shop.slug(), buyerCpf, tickets, adult);
	}

	private static String ticket(String batchId, String cpf, String reason) {
		return "{\"batchId\":\"" + batchId + "\",\"holderName\":\"Titular\",\"holderCpf\":\"" + cpf + "\""
				+ (reason == null ? "" : ",\"halfPriceReason\":\"" + reason + "\"") + "}";
	}

	private int reserved(String batchId) {
		return jdbc.sql("SELECT reserved FROM ticket_batches WHERE id = ?").param(UUID.fromString(batchId))
			.query(Integer.class).single();
	}

	private static String key() {
		return UUID.randomUUID().toString().replace("-", "") + "x";
	}

}
