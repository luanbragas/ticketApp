package com.festa.promoter.web;

import com.festa.TestBrowser;
import com.festa.TestCpf;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
import com.festa.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pronto quando do M7: venda feita pelo link de um promoter aparece atribuída a ele no painel. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PromoterFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	OrderPayments payments;

	@Autowired
	OutboxWorker outbox;

	@Test
	void createsLinksWithReadableUniqueCodes() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);

		add(shop, "{\"name\":\"João Silva\",\"phone\":\"(31) 99999-0000\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.links[0].code").value("joao-silva"))
			.andExpect(jsonPath("$.links[0].url").value(endsWith("/e/" + shop.slug() + "?p=joao-silva")))
			.andExpect(jsonPath("$.links[0].phone").value("31999990000"))
			.andExpect(jsonPath("$.links[0].tickets").value(0));
		add(shop, "{\"name\":\"João Silva\"}")
			.andExpect(jsonPath("$.links[*].code", contains("joao-silva", "joao-silva-2")));
		add(shop, "{\"name\":\"Bia\",\"code\":\"joao-silva\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/promoter-code-taken"));
		add(shop, "{\"name\":\"Bia\",\"code\":\"Bia!!\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-promoter-code"));

		String promoterId = JsonPath.read(shop.producer().get(shop.eventPath() + "/promoters")
			.andReturn().getResponse().getContentAsString(), "$.links[0].promoterId");
		add(shop, "{\"promoterId\":\"" + promoterId + "\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/promoter-already-linked"));
	}

	@Test
	void saleThroughTheLinkCountsForThePromoterAfterPayment() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		add(shop, "{\"name\":\"Caio\",\"code\":\"caio\"}").andExpect(status().isCreated());
		add(shop, "{\"name\":\"Duda\",\"code\":\"duda\"}").andExpect(status().isCreated());

		UUID viaCaio = place(shop, "caio", 2);
		UUID viaNobody = place(shop, "nao-existe", 1);
		UUID unpaid = place(shop, "duda", 1);

		assertThat(promoterOf(viaCaio)).isNotNull();
		assertThat(promoterOf(viaNobody)).isNull();
		assertThat(promoterOf(unpaid)).isNotNull();

		payments.confirmPaid(viaCaio);
		payments.confirmPaid(viaNobody);
		outbox.drain(100);
		// O mesmo OrderPaid de novo não conta duas vezes.
		jdbc.sql("""
				INSERT INTO outbox_events (id, type, aggregate_id, payload, next_attempt_at)
				SELECT gen_random_uuid(), type, aggregate_id, payload, now() - interval '1 minute' FROM outbox_events
				 WHERE type = 'OrderPaid' AND aggregate_id = ?""").param(viaCaio).update();
		outbox.drain(100);

		shop.producer().get(shop.eventPath() + "/promoters")
			.andExpect(jsonPath("$.links[0].code").value("caio"))
			.andExpect(jsonPath("$.links[0].tickets").value(2))
			.andExpect(jsonPath("$.links[0].revenueCents").value(6000))
			// Pedido da Duda ainda não foi pago: não conta.
			.andExpect(jsonPath("$.links[1].tickets").value(0))
			.andExpect(jsonPath("$.tickets").value(2))
			.andExpect(jsonPath("$.revenueCents").value(6000))
			.andExpect(jsonPath("$.canManage").value(true));
	}

	@Test
	void inactiveLinkStopsAttributingNewSales() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String linkId = JsonPath.read(add(shop, "{\"name\":\"Caio\",\"code\":\"caio\"}")
			.andReturn().getResponse().getContentAsString(), "$.links[0].id");

		shop.producer().send(MockMvcRequestBuilders.patch(shop.eventPath() + "/promoters/" + linkId), "{\"active\":false}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.links[0].active").value(false));

		assertThat(promoterOf(place(shop, "caio", 1))).isNull();
	}

	@Test
	void promoterSeesOnlyOwnLinkAndNoPhones() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		TestBrowser promoter = member(shop, "PROMOTER");
		UUID promoterUser = userId(promoter);
		add(shop, "{\"name\":\"Eu Mesmo\",\"phone\":\"31988887777\",\"userId\":\"" + promoterUser + "\",\"code\":\"eu-mesmo\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.links[0].teamMember").value(true));
		add(shop, "{\"name\":\"Outro\",\"phone\":\"31977776666\"}").andExpect(status().isCreated());

		promoter.get(shop.eventPath() + "/promoters")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.links", hasSize(1)))
			.andExpect(jsonPath("$.links[0].code").value("eu-mesmo"))
			.andExpect(jsonPath("$.links[0].phone").value(nullValue()))
			.andExpect(jsonPath("$.canManage").value(false));
		promoter.post(shop.eventPath() + "/promoters", "{\"name\":\"Amigo\"}").andExpect(status().isForbidden());
		promoter.get("/api/v1/orgs/" + shop.org() + "/promoters").andExpect(status().isForbidden());

		member(shop, "CHECKIN_OPERATOR").get(shop.eventPath() + "/promoters").andExpect(status().isForbidden());
	}

	@Test
	void teamMemberMustHaveThePromoterRole() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		UUID operator = userId(member(shop, "CHECKIN_OPERATOR"));

		add(shop, "{\"name\":\"Portaria\",\"userId\":\"" + operator + "\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/user-not-promoter"));
	}

	@Test
	void anotherOrganizationSeesNothing() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		Shop other = TestShop.open(mvc, 100, null);
		add(shop, "{\"name\":\"Caio\"}").andExpect(status().isCreated());

		other.producer().get(shop.eventPath() + "/promoters").andExpect(status().isNotFound());
		other.producer().get("/api/v1/orgs/" + other.org() + "/events/" + shop.eventId() + "/promoters")
			.andExpect(status().isNotFound());
	}

	// ---------- apoio ----------

	private ResultActions add(Shop shop, String json) throws Exception {
		return shop.producer().post(shop.eventPath() + "/promoters", json);
	}

	private UUID place(Shop shop, String promoterCode, int tickets) throws Exception {
		String cpf = TestCpf.random();
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < tickets; i++) {
			items.append(i == 0 ? "" : ",").append("{\"batchId\":\"").append(shop.pista())
				.append("\",\"holderName\":\"Titular\",\"holderCpf\":\"").append(TestCpf.random()).append("\"}");
		}
		String json = new TestBrowser(mvc).send(MockMvcRequestBuilders.post("/api/v1/public/orders")
				.header("Idempotency-Key", UUID.randomUUID().toString().replace("-", "") + "k"), """
				{"event":"%s","buyer":{"name":"Ana","email":"ana@festa.test","cpf":"%s"},"tickets":[%s],
				 "adultDeclared":true,"termsAccepted":true,"promoterCode":"%s"}""".formatted(shop.slug(), cpf, items, promoterCode))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(json, "$.id"));
	}

	private UUID promoterOf(UUID order) {
		return jdbc.sql("SELECT promoter_id FROM orders WHERE id = ?").param(order).query(UUID.class).optional()
			.orElse(null);
	}

	private TestBrowser member(Shop shop, String role) throws Exception {
		String email = role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@festa.test";
		TestBrowser browser = new TestBrowser(mvc);
		browser.post("/api/v1/auth/signup", "{\"name\":\"Membro\",\"email\":\"" + email + "\",\"password\":\"senha-forte-123\"}")
			.andExpect(status().isCreated());
		UUID user = jdbc.sql("SELECT id FROM users WHERE email = ?").param(email).query(UUID.class).single();
		jdbc.sql("INSERT INTO organization_members (id, organization_id, user_id, role) VALUES (?, ?, ?, ?)")
			.params(UUID.randomUUID(), UUID.fromString(shop.org()), user, role).update();
		return browser;
	}

	private UUID userId(TestBrowser browser) throws Exception {
		return UUID.fromString(JsonPath.read(browser.get("/api/v1/auth/me").andReturn().getResponse().getContentAsString(), "$.id"));
	}

}
