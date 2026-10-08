package com.festa.ticketing.web;

import com.festa.TestBrowser;
import com.festa.TestTickets;
import com.festa.TestcontainersConfiguration;
import com.festa.ticketing.app.TicketingService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tipos, lotes, virada e cota de meia pelo painel; disponibilidade na página pública (PLAN.md M3). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TicketCatalogFlowTest {

	private static final Instant STARTS = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TicketingService ticketing;

	@Test
	void firstBatchOpensAndTheRestWait() throws Exception {
		Producer ana = producer();
		String pista = createType(ana, "Pista", false);

		addBatch(ana, pista, "Lote 1", 3000, 100).andExpect(status().isCreated());
		addBatch(ana, pista, "Lote 2", 4000, 100)
			.andExpect(jsonPath("$.types[0].batches[*].name", contains("Lote 1", "Lote 2")))
			.andExpect(jsonPath("$.types[0].batches[*].status", contains("ON_SALE", "SCHEDULED")))
			.andExpect(jsonPath("$.types[0].batches[0].priceCents").value(3000))
			.andExpect(jsonPath("$.types[0].batches[0].remaining").value(100));
	}

	/** Pronto quando do M3: evento com 2 tipos e 3 lotes vira de lote sozinho por quantidade e por data. */
	@Test
	void twoTypesThreeBatchesTurnByQuantityAndByDate() throws Exception {
		Producer ana = producer();
		String pista = createType(ana, "Pista", false);
		String meia = createType(ana, "Pista meia", true);
		addBatch(ana, pista, "Lote 1", 3000, 100);
		addBatch(ana, pista, "Lote 2", 4000, 100);
		addBatch(ana, meia, "Lote 1", 1500, 80);

		// Quantidade: o Lote 1 da pista esgota (pagos = capacidade).
		jdbc.sql("UPDATE ticket_batches SET sold = capacity WHERE ticket_type_id = ? AND position = 0")
			.param(UUID.fromString(pista)).update();
		runJob();
		catalog(ana)
			.andExpect(jsonPath("$.types[0].batches[*].status", contains("SOLD_OUT", "ON_SALE")))
			.andExpect(jsonPath("$.types[1].batches[*].status", contains("ON_SALE")));

		// Data: a virada do Lote 2 chega com ingresso sobrando; sem próximo lote, o tipo fica sem venda.
		jdbc.sql("UPDATE ticket_batches SET sales_end_at = now() - interval '1 minute' WHERE ticket_type_id = ? AND position = 1")
			.param(UUID.fromString(pista)).update();
		runJob();
		catalog(ana)
			.andExpect(jsonPath("$.types[0].batches[*].status", contains("SOLD_OUT", "CLOSED")))
			.andExpect(jsonPath("$.types[1].batches[*].status", contains("ON_SALE")));
	}

	@Test
	void closingByHandOpensTheNextAndClosedNeverReopens() throws Exception {
		Producer ana = producer();
		String pista = createType(ana, "Pista", false);
		String lote1 = batchId(addBatch(ana, pista, "Lote 1", 3000, 100), 0, 0);
		addBatch(ana, pista, "Lote 2", 4000, 100);

		ana.browser.post(ana.event + "/batches/" + lote1 + "/close", "{}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.types[0].batches[*].status", contains("CLOSED", "ON_SALE")));

		ana.put(ana.event + "/batches/" + lote1, batchJson("Lote 1", 3000, 200))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/batch-closed"));
		ana.browser.post(ana.event + "/batches/" + lote1 + "/close", "{}").andExpect(status().isConflict());
	}

	@Test
	void openingDateInTheFutureKeepsTheBatchScheduled() throws Exception {
		Producer ana = producer();
		String pista = createType(ana, "Pista", false);
		Instant opens = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

		ana.browser.post(ana.event + "/ticket-types/" + pista + "/batches",
				"{\"name\":\"Lote 1\",\"priceCents\":3000,\"capacity\":100,\"salesStartAt\":\"" + opens + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.types[0].batches[0].status").value("SCHEDULED"))
			.andExpect(jsonPath("$.types[0].batches[0].salesStartAt").value(opens.toString()));
	}

	@Test
	void capacityNeverGoesBelowWhatWasSold() throws Exception {
		Producer ana = producer();
		String pista = createType(ana, "Pista", false);
		String lote1 = batchId(addBatch(ana, pista, "Lote 1", 3000, 100), 0, 0);
		jdbc.sql("UPDATE ticket_batches SET sold = 30, reserved = 5 WHERE id = ?").param(UUID.fromString(lote1)).update();

		ana.put(ana.event + "/batches/" + lote1, batchJson("Lote 1", 3500, 20))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/capacity-below-sold"));
		ana.put(ana.event + "/batches/" + lote1, batchJson("Lote 1 promo", 3500, 35))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.types[0].batches[0].priceCents").value(3500))
			.andExpect(jsonPath("$.types[0].batches[0].sold").value(30))
			.andExpect(jsonPath("$.types[0].batches[0].remaining").value(0));

		// Lote com venda não some: o pedido aponta para ele.
		ana.browser.send(MockMvcRequestBuilders.delete(ana.event + "/batches/" + lote1), "")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/batch-has-sales"));
		ana.browser.send(MockMvcRequestBuilders.delete(ana.event + "/ticket-types/" + pista), "")
			.andExpect(status().isConflict());
	}

	@Test
	void deletingTheBatchOnSaleOpensTheNext() throws Exception {
		Producer ana = producer();
		String pista = createType(ana, "Pista", false);
		String lote1 = batchId(addBatch(ana, pista, "Lote 1", 3000, 100), 0, 0);
		addBatch(ana, pista, "Lote 2", 4000, 100);

		ana.browser.send(MockMvcRequestBuilders.delete(ana.event + "/batches/" + lote1), "")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.types[0].batches[*].name", contains("Lote 2")))
			.andExpect(jsonPath("$.types[0].batches[0].status").value("ON_SALE"));
	}

	@Test
	void halfPriceQuotaIsReported() throws Exception {
		Producer ana = producer();
		String pista = createType(ana, "Pista", false);
		String meia = createType(ana, "Meia", true);
		addBatch(ana, pista, "Lote 1", 3000, 300);

		addBatch(ana, meia, "Lote 1", 1500, 100)
			.andExpect(jsonPath("$.halfPriceQuota.percent").value(40))
			.andExpect(jsonPath("$.halfPriceQuota.total").value(400))
			.andExpect(jsonPath("$.halfPriceQuota.minimum").value(160))
			.andExpect(jsonPath("$.halfPriceQuota.halfPrice").value(100))
			.andExpect(jsonPath("$.halfPriceQuota.met").value(false))
			.andExpect(jsonPath("$.types[1].halfPrice").value(true));

		ana.patch(ana.event, "{\"halfPriceQuotaPercent\":25}").andExpect(status().isOk());
		catalog(ana).andExpect(jsonPath("$.halfPriceQuota.met").value(true));
	}

	@Test
	void renamesAndDeletesTypeWithoutSales() throws Exception {
		Producer ana = producer();
		String pista = createType(ana, "Pista", false);
		addBatch(ana, pista, "Lote 1", 3000, 100);

		ana.patch(ana.event + "/ticket-types/" + pista, "{\"name\":\"Pista premium\",\"description\":\"Frente do palco\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.types[0].name").value("Pista premium"))
			.andExpect(jsonPath("$.types[0].description").value("Frente do palco"));
		ana.browser.send(MockMvcRequestBuilders.delete(ana.event + "/ticket-types/" + pista), "")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.types", hasSize(0)));
	}

	@Test
	void rejectsInvalidBatch() throws Exception {
		Producer ana = producer();
		String pista = createType(ana, "Pista", false);
		Instant at = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

		addBatch(ana, pista, "Lote 1", 0, 100).andExpect(status().isBadRequest());
		ana.browser.post(ana.event + "/ticket-types/" + pista + "/batches",
				"{\"name\":\"Lote 1\",\"priceCents\":3000,\"capacity\":100,\"salesStartAt\":\"" + at
						+ "\",\"salesEndAt\":\"" + at + "\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-sales-window"));
	}

	@Test
	void publishRequiresTickets() throws Exception {
		Producer ana = producer();
		makeReadyExceptTickets(ana);

		ana.browser.post(ana.event + "/publish", "{}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.missing", contains("tickets")));

		TestTickets.addPista(ana.browser, ana.event);
		ana.browser.post(ana.event + "/publish", "{}").andExpect(status().isOk());
	}

	@Test
	void publicPageShowsVisibleBatchesOfPublishedEventOnly() throws Exception {
		Producer ana = producer();
		makeReadyExceptTickets(ana);
		String pista = createType(ana, "Pista", false);
		addBatch(ana, pista, "Lote 1", 3000, 100);
		ana.browser.post(ana.event + "/ticket-types/" + pista + "/batches",
				"{\"name\":\"Lote secreto\",\"priceCents\":2000,\"capacity\":10,\"visible\":false}")
			.andExpect(status().isCreated());
		createType(ana, "Camarote", false);
		String slug = JsonPath.read(ana.browser.get(ana.event).andReturn().getResponse().getContentAsString(), "$.slug");
		TestBrowser visitor = new TestBrowser(mvc);

		visitor.get("/api/v1/public/events/" + slug + "/availability").andExpect(status().isNotFound());

		ana.browser.post(ana.event + "/publish", "{}").andExpect(status().isOk());
		jdbc.sql("UPDATE ticket_batches SET sold = 92 WHERE ticket_type_id = ? AND position = 0")
			.param(UUID.fromString(pista)).update();
		visitor.get("/api/v1/public/events/" + slug + "/availability")
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "max-age=5, public"))
			.andExpect(jsonPath("$.eventStatus").value("PUBLISHED"))
			.andExpect(jsonPath("$.types", hasSize(1)))
			.andExpect(jsonPath("$.types[0].name").value("Pista"))
			.andExpect(jsonPath("$.types[0].batches[*].name", contains("Lote 1")))
			.andExpect(jsonPath("$.types[0].batches[0].availability").value("LAST_UNITS"))
			.andExpect(jsonPath("$.types[0].batches[0].maxPerOrder").value(10))
			.andExpect(jsonPath("$.types[0].batches[0].sold").doesNotExist())
			.andExpect(jsonPath("$.types[0].batches[0].capacity").doesNotExist());
	}

	@Test
	void anotherOrganizationAndPromoterCannotTouchTickets() throws Exception {
		Producer ana = producer();
		String pista = createType(ana, "Pista", false);
		Producer bia = producer();
		String path = "/api/v1/orgs/" + bia.org + "/events/" + ana.eventId;

		bia.browser.get(path + "/ticket-types").andExpect(status().isNotFound());
		bia.browser.post(path + "/ticket-types", "{\"name\":\"Invasão\"}").andExpect(status().isNotFound());
		bia.browser.get(ana.event + "/ticket-types").andExpect(status().isNotFound());

		Producer promoter = memberOf(ana, "PROMOTER");
		promoter.browser.get(ana.event + "/ticket-types").andExpect(status().isOk());
		promoter.browser.post(ana.event + "/ticket-types/" + pista + "/batches", batchJson("Lote 1", 3000, 10))
			.andExpect(status().isForbidden());
	}

	@Test
	void cancelledEventFreezesTickets() throws Exception {
		Producer ana = producer();
		String pista = createType(ana, "Pista", false);
		ana.browser.post(ana.event + "/cancel", "{}").andExpect(status().isOk());

		addBatch(ana, pista, "Lote 1", 3000, 100)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-event-status"));
	}

	@Test
	void jobFindsOnlyEventsWithSomethingToTurn() throws Exception {
		Producer ana = producer();
		String pista = createType(ana, "Pista", false);
		addBatch(ana, pista, "Lote 1", 3000, 100);
		addBatch(ana, pista, "Lote 2", 4000, 100);

		org.assertj.core.api.Assertions.assertThat(ticketing.eventsDueForRollover())
			.noneMatch(row -> row[0].equals(UUID.fromString(ana.eventId)));

		jdbc.sql("UPDATE ticket_batches SET sold = capacity WHERE ticket_type_id = ? AND position = 0")
			.param(UUID.fromString(pista)).update();
		org.assertj.core.api.Assertions.assertThat(ticketing.eventsDueForRollover())
			.anyMatch(row -> row[0].equals(UUID.fromString(ana.eventId)));
	}

	// ---------- apoio ----------

	private record Producer(TestBrowser browser, String org, String eventId, String event) {

		ResultActions patch(String url, String json) throws Exception {
			return browser.send(MockMvcRequestBuilders.patch(url), json);
		}

		ResultActions put(String url, String json) throws Exception {
			return browser.send(MockMvcRequestBuilders.put(url), json);
		}

	}

	/** Roda a virada como o job faz. */
	private void runJob() {
		ticketing.eventsDueForRollover().forEach(row -> ticketing.rolloverEvent(row[1], row[0]));
	}

	private ResultActions catalog(Producer producer) throws Exception {
		return producer.browser.get(producer.event + "/ticket-types").andExpect(status().isOk());
	}

	private String createType(Producer producer, String name, boolean halfPrice) throws Exception {
		String json = producer.browser.post(producer.event + "/ticket-types",
				"{\"name\":\"" + name + "\",\"halfPrice\":" + halfPrice + "}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.types[-1].id");
	}

	private ResultActions addBatch(Producer producer, String typeId, String name, long price, int capacity)
			throws Exception {
		return producer.browser.post(producer.event + "/ticket-types/" + typeId + "/batches",
				batchJson(name, price, capacity));
	}

	private static String batchJson(String name, long price, int capacity) {
		return "{\"name\":\"" + name + "\",\"priceCents\":" + price + ",\"capacity\":" + capacity + "}";
	}

	private static String batchId(ResultActions result, int type, int batch) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(),
				"$.types[" + type + "].batches[" + batch + "].id");
	}

	private void makeReadyExceptTickets(Producer producer) throws Exception {
		producer.patch(producer.event, "{\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"venueName\":\"Galpão 42\"}"
			.formatted(STARTS, STARTS.plus(6, ChronoUnit.HOURS))).andExpect(status().isOk());
		producer.put(producer.event + "/media/flyer", "{\"key\":\"orgs/" + producer.org + "/events/" + producer.eventId
				+ "/flyer/" + UUID.randomUUID() + ".jpg\",\"width\":918,\"height\":1600}")
			.andExpect(status().isOk());
	}

	private Producer producer() throws Exception {
		TestBrowser browser = signup("tix-" + unique() + "@festa.test");
		String org = JsonPath.read(browser.post("/api/v1/orgs", "{\"name\":\"Atlética " + unique() + "\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		String eventId = JsonPath.read(browser.post("/api/v1/orgs/" + org + "/events", "{\"name\":\"Calourada\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		return new Producer(browser, org, eventId, "/api/v1/orgs/" + org + "/events/" + eventId);
	}

	private Producer memberOf(Producer owner, String role) throws Exception {
		String email = role.toLowerCase() + "-" + unique() + "@festa.test";
		TestBrowser browser = signup(email);
		UUID userId = jdbc.sql("SELECT id FROM users WHERE email = ?").param(email).query(UUID.class).single();
		jdbc.sql("INSERT INTO organization_members (id, organization_id, user_id, role) VALUES (?, ?, ?, ?)")
			.params(UUID.randomUUID(), UUID.fromString(owner.org), userId, role).update();
		return new Producer(browser, owner.org, owner.eventId, owner.event);
	}

	private TestBrowser signup(String email) throws Exception {
		TestBrowser browser = new TestBrowser(mvc);
		browser.post("/api/v1/auth/signup",
				"{\"name\":\"Produtor\",\"email\":\"" + email + "\",\"password\":\"senha-forte-123\"}")
			.andExpect(status().isCreated());
		return browser;
	}

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
