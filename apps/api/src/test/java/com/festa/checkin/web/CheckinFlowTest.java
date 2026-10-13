package com.festa.checkin.web;

import com.festa.TestBrowser;
import com.festa.TestCpf;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
import com.festa.TestcontainersConfiguration;
import com.festa.order.app.CheckoutService;
import com.festa.order.app.CheckoutService.Buyer;
import com.festa.order.app.CheckoutService.PlaceOrder;
import com.festa.order.app.CheckoutService.TicketRequest;
import com.festa.order.app.OrderPayments;
import com.festa.shared.outbox.OutboxWorker;
import com.festa.ticketing.app.TicketQueries;
import com.festa.ticketing.domain.TicketTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Portaria (PLAN.md M8): online, busca manual, offline com conflito, desfazer, participantes e dashboard. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CheckinFlowTest {

	private static final String DEVICE_A = "aparelho-portaria-a";
	private static final String DEVICE_B = "aparelho-portaria-b";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	CheckoutService checkout;

	@Autowired
	OrderPayments payments;

	@Autowired
	OutboxWorker outbox;

	@Autowired
	TicketQueries tickets;

	@Test
	void validTicketEntersOnceThenShowsWhenItEntered() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		List<String> tokens = issue(shop, "Ana", 2);

		scan(shop.producer(), shop, tokens.getFirst())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome").value("ADMITTED"))
			.andExpect(jsonPath("$.ticket.holderName").value("Ana 0"))
			.andExpect(jsonPath("$.ticket.typeName").value("Pista"))
			.andExpect(jsonPath("$.checkedInAt").value(notNullValue()));
		scan(shop.producer(), shop, tokens.getFirst())
			.andExpect(jsonPath("$.outcome").value("ALREADY_IN"))
			.andExpect(jsonPath("$.checkedInAt").value(notNullValue()));
		scan(shop.producer(), shop, "A".repeat(43)).andExpect(jsonPath("$.outcome").value("NOT_VALID"));
		scan(shop.producer(), shop, "nada").andExpect(jsonPath("$.outcome").value("NOT_VALID"));

		// QR de outra festa não entra aqui.
		Shop other = TestShop.open(mvc, 100, null);
		String foreign = issue(other, "Bia", 1).getFirst();
		scan(shop.producer(), shop, foreign)
			.andExpect(jsonPath("$.outcome").value("NOT_VALID"))
			.andExpect(jsonPath("$.ticket").value(nullValue()));
	}

	@Test
	void manualSearchByNameOrCpf() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String cpf = TestCpf.random();
		UUID order = order(shop, List.of(new TicketRequest(UUID.fromString(shop.pista()), "Carla Mendes", cpf, null)));
		pay(order);

		String json = shop.producer().get(shop.eventPath() + "/attendees?q=carla")
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].holderCpf").value("***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**"))
			.andReturn().getResponse().getContentAsString();
		shop.producer().get(shop.eventPath() + "/attendees?q=" + cpf).andExpect(jsonPath("$.items", hasSize(1)));
		shop.producer().get(shop.eventPath() + "/attendees?q=ninguem").andExpect(jsonPath("$.items", hasSize(0)));

		String ticketId = JsonPath.read(json, "$.items[0].ticket.ticketId");
		shop.producer().post(shop.eventPath() + "/checkins", "{\"ticketId\":\"" + ticketId + "\"}")
			.andExpect(jsonPath("$.outcome").value("ADMITTED"));
		shop.producer().get(shop.eventPath() + "/attendees?status=CHECKED_IN")
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.checkedIn").value(1));
	}

	/** Pronto quando do M8: 50 ingressos lidos em modo avião sincronizam depois. */
	@Test
	void fiftyTicketsReadOfflineSyncLater() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		List<String> tokens = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			tokens.addAll(issue(shop, "Lote" + i, 10));
		}
		// O aparelho baixa a lista antes de perder a internet.
		shop.producer().get(shop.eventPath() + "/checkin/manifest")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tickets", hasSize(50)))
			.andExpect(jsonPath("$.tickets[0].tokenHash").value(org.hamcrest.Matchers.matchesPattern("^[0-9a-f]{64}$")));

		Instant start = Instant.now().minus(30, ChronoUnit.MINUTES);
		String items = String.join(",", IntStream.range(0, 50)
			.mapToObj(i -> "{\"tokenHash\":\"" + TicketTokens.hash(tokens.get(i)) + "\",\"checkedInAt\":\""
					+ start.plusSeconds(i) + "\"}")
			.toList());
		String body = "{\"deviceId\":\"" + DEVICE_A + "\",\"items\":[" + items + "]}";

		shop.producer().post(shop.eventPath() + "/checkins/sync", body)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.results", hasSize(50)))
			.andExpect(jsonPath("$.results[?(@.outcome == 'ACCEPTED')]", hasSize(50)));
		// A rede caiu na resposta e o aparelho reenviou: nada muda.
		shop.producer().post(shop.eventPath() + "/checkins/sync", body)
			.andExpect(jsonPath("$.results[?(@.outcome == 'ACCEPTED')]", hasSize(50)));

		shop.producer().get(shop.eventPath() + "/attendees?limit=1")
			.andExpect(jsonPath("$.checkedIn").value(50))
			.andExpect(jsonPath("$.duplicates").value(0));
		assertThat(jdbc.sql("SELECT count(*) FROM checkins WHERE event_id = ?").param(UUID.fromString(shop.eventId()))
			.query(Integer.class).single()).isEqualTo(50);
	}

	@Test
	void earliestReadWinsAndTheOtherGoesToTheConflictReport() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String token = issue(shop, "Duda", 1).getFirst();

		// Aparelho B, online, lê agora; aparelho A tinha lido 10 min antes, sem internet.
		scan(shop.producer(), shop, token).andExpect(jsonPath("$.outcome").value("ADMITTED"));
		Instant earlier = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
		shop.producer().post(shop.eventPath() + "/checkins/sync", "{\"deviceId\":\"" + DEVICE_A
				+ "\",\"items\":[{\"tokenHash\":\"" + TicketTokens.hash(token) + "\",\"checkedInAt\":\"" + earlier + "\"},"
				+ "{\"tokenHash\":\"" + "0".repeat(64) + "\",\"checkedInAt\":\"" + earlier + "\"}]}")
			.andExpect(jsonPath("$.results[0].outcome").value("ACCEPTED"))
			.andExpect(jsonPath("$.results[1].outcome").value("INVALID"));
		// Um terceiro leu depois: duplicado.
		shop.producer().post(shop.eventPath() + "/checkins/sync", "{\"deviceId\":\"" + DEVICE_B
				+ "\",\"items\":[{\"tokenHash\":\"" + TicketTokens.hash(token) + "\",\"checkedInAt\":\"" + Instant.now() + "\"}]}")
			.andExpect(jsonPath("$.results[0].outcome").value("DUPLICATE"));

		shop.producer().get(shop.eventPath() + "/attendees")
			.andExpect(jsonPath("$.items[0].checkedInAt").value(earlier.toString()))
			.andExpect(jsonPath("$.duplicates").value(2));
	}

	@Test
	void ownerUndoesWithAuditLogAndTheTicketWorksAgain() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String token = issue(shop, "Edu", 1).getFirst();
		scan(shop.producer(), shop, token).andExpect(jsonPath("$.outcome").value("ADMITTED"));
		String checkinId = JsonPath.read(shop.producer().get(shop.eventPath() + "/attendees")
			.andReturn().getResponse().getContentAsString(), "$.items[0].checkinId");
		String undo = "/api/v1/orgs/" + shop.org() + "/checkins/" + checkinId + "/undo";

		member(shop, "MANAGER").post(undo, "{}").andExpect(status().isForbidden());
		shop.producer().post(undo, "{}").andExpect(status().isNoContent());
		shop.producer().post(undo, "{}").andExpect(status().isConflict());

		assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = 'checkin.undone' AND organization_id = ?")
			.param(UUID.fromString(shop.org())).query(Integer.class).single()).isEqualTo(1);
		scan(shop.producer(), shop, token).andExpect(jsonPath("$.outcome").value("ADMITTED"));
	}

	@Test
	void operatorSeesOnlyNameTypeAndStatus() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		String token = issue(shop, "Fabi", 1).getFirst();
		TestBrowser operator = member(shop, "CHECKIN_OPERATOR");

		scan(operator, shop, token).andExpect(jsonPath("$.outcome").value("ADMITTED"));
		operator.get(shop.eventPath() + "/attendees?q=fabi")
			.andExpect(jsonPath("$.items[0].ticket.holderName").value("Fabi 0"))
			.andExpect(jsonPath("$.items[0].holderCpf").value(nullValue()))
			.andExpect(jsonPath("$.items[0].buyerEmail").value(nullValue()));
		operator.get(shop.eventPath() + "/dashboard").andExpect(status().isForbidden());

		member(shop, "PROMOTER").get(shop.eventPath() + "/checkin/manifest").andExpect(status().isForbidden());
		TestShop.open(mvc, 100, null).producer().get(shop.eventPath() + "/checkin/manifest")
			.andExpect(status().isNotFound());
	}

	@Test
	void dashboardShowsSalesStockEntriesAndSalesPerDay() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		List<String> tokens = issue(shop, "Gabi", 3);
		order(shop, List.of(new TicketRequest(UUID.fromString(shop.pista()), "Pendente", TestCpf.random(), null)));
		scan(shop.producer(), shop, tokens.getFirst());

		shop.producer().get(shop.eventPath() + "/dashboard")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paidOrders").value(1))
			.andExpect(jsonPath("$.ticketsSold").value(3))
			.andExpect(jsonPath("$.revenueCents").value(9000))
			.andExpect(jsonPath("$.feeCents").value(900))
			.andExpect(jsonPath("$.pendingOrders").value(1))
			.andExpect(jsonPath("$.capacity").value(150))
			.andExpect(jsonPath("$.sold").value(3))
			.andExpect(jsonPath("$.reserved").value(1))
			.andExpect(jsonPath("$.issued").value(3))
			.andExpect(jsonPath("$.checkedIn").value(1))
			.andExpect(jsonPath("$.byDay", hasSize(1)))
			.andExpect(jsonPath("$.byDay[0].tickets").value(3));
	}

	// ---------- apoio ----------

	private ResultActions scan(TestBrowser browser, Shop shop, String token) throws Exception {
		return browser.post(shop.eventPath() + "/checkins",
				"{\"token\":\"" + token + "\",\"deviceId\":\"" + DEVICE_B + "\"}");
	}

	/** Pedido pago de {@code count} ingressos de Pista; devolve os tokens dos QRs. */
	private List<String> issue(Shop shop, String name, int count) {
		List<TicketRequest> requests = IntStream.range(0, count)
			.mapToObj(i -> new TicketRequest(UUID.fromString(shop.pista()), name + " " + i, TestCpf.random(), null))
			.toList();
		UUID order = order(shop, requests);
		pay(order);
		return tickets.forOrder(order).stream().map(TicketQueries.TicketView::token).toList();
	}

	private UUID order(Shop shop, List<TicketRequest> requests) {
		String cpf = TestCpf.random();
		return checkout.place(new PlaceOrder(shop.slug(), UUID.randomUUID().toString().replace("-", "") + "k",
				new Buyer("Comprador", "c@festa.test", null, cpf), requests, true, true, null)).order().getId();
	}

	private void pay(UUID order) {
		payments.confirmPaid(order);
		outbox.drain(100);
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

}
