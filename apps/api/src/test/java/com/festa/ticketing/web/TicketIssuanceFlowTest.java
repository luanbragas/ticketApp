package com.festa.ticketing.web;

import com.festa.TestBrowser;
import com.festa.TestCpf;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
import com.festa.TestcontainersConfiguration;
import com.festa.notification.RecordingEmailSender;
import com.festa.notification.api.EmailMessage;
import com.festa.order.app.OrderPayments;
import com.festa.shared.outbox.OutboxWorker;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pronto quando do M6: depois de pagar, o comprador recebe o e-mail com os ingressos e vê tudo em "Meus
 * ingressos". O pagamento é confirmado como o webhook do M5 vai fazer ({@link OrderPayments#confirmPaid}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, RecordingEmailSender.Config.class })
class TicketIssuanceFlowTest {

	private static final Pattern TICKET_LINK = Pattern.compile("/t/([A-Za-z0-9_-]{43})");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	OrderPayments payments;

	@Autowired
	OutboxWorker outbox;

	@Autowired
	RecordingEmailSender emails;

	@Test
	void paidOrderBecomesOneTicketPerItemAndAnEmailWithTheLinks() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String email = "compra-" + unique() + "@festa.test";
		String buyerCpf = TestCpf.random();
		UUID order = place(shop, email, buyerCpf, ticket(shop.pista(), "Ana Souza", buyerCpf, null) + ","
				+ ticket(shop.meia(), "Bia Lima", TestCpf.random(), "STUDENT"));

		assertThat(payments.confirmPaid(order)).isTrue();
		outbox.drain(100);

		assertThat(jdbc.sql("SELECT status FROM orders WHERE id = ?").param(order).query(String.class).single())
			.isEqualTo("PAID");
		assertThat(batch(shop.pista(), "sold")).isEqualTo(1);
		assertThat(batch(shop.pista(), "reserved")).isZero();
		assertThat(tickets(order)).isEqualTo(2);

		List<EmailMessage> sent = emails.sentTo(email);
		assertThat(sent).hasSize(1);
		assertThat(sent.getFirst().subject()).startsWith("Seus ingressos: Calourada");
		List<String> tokens = tokensIn(sent.getFirst().text());
		assertThat(tokens).hasSize(2);
		assertThat(sent.getFirst().text()).contains("Ana Souza", "Bia Lima", "meia: leve o documento", "/meus-ingressos")
			.containsPattern(" às \\d{1,2}h(\\d{2})?\n");
		// Nem o token nem o CPF ficam no banco em claro.
		assertThat(jdbc.sql("SELECT count(*) FROM tickets WHERE token_hash = ?").param(tokens.getFirst())
			.query(Integer.class).single()).isZero();

		TestBrowser visitor = new TestBrowser(mvc);
		visitor.get("/api/v1/public/tickets/" + tokens.getFirst())
			.andExpect(status().isOk())
			.andExpect(header().string("Referrer-Policy", "no-referrer"))
			.andExpect(jsonPath("$.status").value("VALID"))
			.andExpect(jsonPath("$.holderName").value("Ana Souza"))
			.andExpect(jsonPath("$.holderCpf").value("***." + buyerCpf.substring(3, 6) + "." + buyerCpf.substring(6, 9) + "-**"))
			.andExpect(jsonPath("$.typeName").value("Pista"))
			.andExpect(jsonPath("$.event.venueName").value("Galpão 42"))
			.andExpect(jsonPath("$.id").doesNotExist())
			.andExpect(jsonPath("$.orderId").doesNotExist());
		visitor.get("/api/v1/public/tickets/" + "A".repeat(43)).andExpect(status().isNotFound());
		visitor.get("/api/v1/public/tickets/nao-e-token").andExpect(status().isNotFound());
	}

	@Test
	void repeatedPaymentOrEventNeverDuplicatesTicketsOrEmail() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String email = "repete-" + unique() + "@festa.test";
		String cpf = TestCpf.random();
		UUID order = place(shop, email, cpf, ticket(shop.pista(), "Ana", cpf, null));

		assertThat(payments.confirmPaid(order)).isTrue();
		assertThat(payments.confirmPaid(order)).isFalse();
		outbox.drain(100);
		// O outbox entrega "pelo menos uma vez": o mesmo OrderPaid de novo não emite nada.
		jdbc.sql("""
				INSERT INTO outbox_events (id, type, aggregate_id, payload)
				SELECT gen_random_uuid(), type, aggregate_id, payload FROM outbox_events
				 WHERE type = 'OrderPaid' AND aggregate_id = ?""").param(order).update();
		outbox.drain(100);

		assertThat(tickets(order)).isEqualTo(1);
		assertThat(batch(shop.pista(), "sold")).isEqualTo(1);
		assertThat(emails.sentTo(email)).hasSize(1);
	}

	@Test
	void sellingTheLastTicketTurnsTheBatch() throws Exception {
		Shop shop = TestShop.open(mvc, 1, null);
		String cpf = TestCpf.random();
		UUID order = place(shop, "ultimo-" + unique() + "@festa.test", cpf, ticket(shop.pista(), "Ana", cpf, null));

		payments.confirmPaid(order);

		assertThat(jdbc.sql("SELECT status FROM ticket_batches WHERE id = ?").param(UUID.fromString(shop.pista()))
			.query(String.class).single()).isEqualTo("SOLD_OUT");
	}

	@Test
	void myTicketsNeedAVerifiedEmail() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String email = "meus-" + unique() + "@festa.test";
		String cpf = TestCpf.random();
		UUID order = place(shop, email, cpf, ticket(shop.pista(), "Ana", cpf, null) + ","
				+ ticket(shop.pista(), "Caio", TestCpf.random(), null));
		payments.confirmPaid(order);
		outbox.drain(100);

		TestBrowser buyer = new TestBrowser(mvc);
		buyer.post("/api/v1/auth/signup",
				"{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"senha-forte-123\"}")
			.andExpect(status().isCreated());
		buyer.get("/api/v1/me/tickets")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/email-not-verified"));

		// O link mágico confirma o e-mail (ADR-004); aqui marcamos direto.
		jdbc.sql("UPDATE users SET email_verified_at = now() WHERE email = ?").param(email).update();
		buyer.get("/api/v1/me/tickets")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.upcoming", hasSize(2)))
			.andExpect(jsonPath("$.past", hasSize(0)))
			.andExpect(jsonPath("$.upcoming[*].holderName").value(org.hamcrest.Matchers.containsInAnyOrder("Ana", "Caio")))
			.andExpect(jsonPath("$.upcoming[0].token").value(org.hamcrest.Matchers.matchesPattern("^[A-Za-z0-9_-]{43}$")));

		new TestBrowser(mvc).get("/api/v1/me/tickets").andExpect(status().isUnauthorized());
	}

	// ---------- apoio ----------

	private UUID place(Shop shop, String email, String buyerCpf, String tickets) throws Exception {
		String json = new TestBrowser(mvc).send(MockMvcRequestBuilders.post("/api/v1/public/orders")
				.header("Idempotency-Key", UUID.randomUUID().toString().replace("-", "") + "k"), """
				{"event":"%s","buyer":{"name":"Ana Souza","email":"%s","cpf":"%s"},
				 "tickets":[%s],"adultDeclared":true,"termsAccepted":true}""".formatted(shop.slug(), email, buyerCpf, tickets))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(json, "$.id"));
	}

	private static String ticket(String batchId, String holder, String cpf, String reason) {
		return "{\"batchId\":\"" + batchId + "\",\"holderName\":\"" + holder + "\",\"holderCpf\":\"" + cpf + "\""
				+ (reason == null ? "" : ",\"halfPriceReason\":\"" + reason + "\"") + "}";
	}

	private int tickets(UUID order) {
		return jdbc.sql("SELECT count(*) FROM tickets WHERE order_id = ?").param(order).query(Integer.class).single();
	}

	private int batch(String batchId, String column) {
		return jdbc.sql("SELECT " + column + " FROM ticket_batches WHERE id = ?").param(UUID.fromString(batchId))
			.query(Integer.class).single();
	}

	private static List<String> tokensIn(String text) {
		Matcher matcher = TICKET_LINK.matcher(text);
		java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
		while (matcher.find()) {
			tokens.add(matcher.group(1));
		}
		return tokens;
	}

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
