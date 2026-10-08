package com.festa.event.web;

import com.festa.TestBrowser;
import com.festa.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRUD e ciclo de vida do evento pelo painel, com isolamento entre organizações e papéis (SECURITY.md). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventFlowTest {

	private static final Instant STARTS = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
	private static final Instant ENDS = STARTS.plus(6, ChronoUnit.HOURS);

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void createsDraftWithSlugFromName() throws Exception {
		Producer ana = producer();
		String name = "Calourada Med " + unique();

		ana.browser.post(events(ana), "{\"name\":\"" + name + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.slug").value(org.hamcrest.Matchers.startsWith("calourada-med-")))
			.andExpect(jsonPath("$.minAge").value(18))
			.andExpect(jsonPath("$.flyer").value(nullValue()))
			.andExpect(jsonPath("$.pageUrl").value(org.hamcrest.Matchers.containsString("/e/calourada-med-")));
	}

	@Test
	void sameNameGetsNumberedSlug() throws Exception {
		Producer ana = producer();
		String name = "Baile " + unique();

		String first = slugOf(ana.browser.post(events(ana), "{\"name\":\"" + name + "\"}").andReturn()
			.getResponse().getContentAsString());
		ana.browser.post(events(ana), "{\"name\":\"" + name + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value(first + "-2"));
	}

	@Test
	void editsDetailsAndReplacesLineup() throws Exception {
		Producer ana = producer();
		String event = createEvent(ana);

		ana.patch(event(ana, event), """
				{"description":"Open bar até 3h","startsAt":"%s","endsAt":"%s","venueName":"Galpão 42",
				 "city":"Belo Horizonte","hasOpenBar":true,"accentColor":"#e8262c",
				 "lineup":[{"name":"DJ Kaio Ramos","startsAt":"%s"},{"name":"MC Lia"}]}""".formatted(STARTS, ENDS, STARTS))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.venueName").value("Galpão 42"))
			.andExpect(jsonPath("$.hasOpenBar").value(true))
			.andExpect(jsonPath("$.accentColor").value("#e8262c"))
			.andExpect(jsonPath("$.lineup[*].name", contains("DJ Kaio Ramos", "MC Lia")));

		ana.patch(event(ana, event), "{\"lineup\":[{\"name\":\"Bateria da Med\"}]}")
			.andExpect(jsonPath("$.lineup", hasSize(1)))
			.andExpect(jsonPath("$.lineup[0].name").value("Bateria da Med"))
			.andExpect(jsonPath("$.description").value("Open bar até 3h"));
	}

	@Test
	void validatesFieldsAndBusinessRules() throws Exception {
		Producer ana = producer();
		String event = createEvent(ana);

		ana.patch(event(ana, event), "{\"accentColor\":\"red\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("accentColor"));
		ana.patch(event(ana, event), "{\"startsAt\":\"%s\",\"endsAt\":\"%s\"}".formatted(ENDS, STARTS))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-dates"));
		ana.patch(event(ana, event), "{\"minAge\":16,\"hasOpenBar\":true}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/open-bar-needs-18"));
	}

	@Test
	void publishRequiresDateVenueAndFlyer() throws Exception {
		Producer ana = producer();
		String event = createEvent(ana);

		ana.browser.post(event(ana, event) + "/publish", "{}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/event-not-ready"))
			.andExpect(jsonPath("$.missing", contains("startsAt", "endsAt", "venueName", "flyer")));
	}

	@Test
	void fullLifecycleWithFlyer() throws Exception {
		Producer ana = producer();
		String event = createEvent(ana);
		ana.patch(event(ana, event), "{\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"venueName\":\"Galpão 42\"}"
			.formatted(STARTS, ENDS)).andExpect(status().isOk());

		String key = "orgs/" + ana.org + "/events/" + event + "/flyer/" + UUID.randomUUID() + ".jpg";
		ana.put(event(ana, event) + "/media/flyer", "{\"key\":\"" + key + "\",\"width\":918,\"height\":1600}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.flyer.url").value(endsWith(key)))
			.andExpect(jsonPath("$.flyer.height").value(1600));

		ana.browser.post(event(ana, event) + "/publish", "{}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.publishedAt").exists());
		ana.browser.post(event(ana, event) + "/publish", "{}").andExpect(status().isConflict());

		ana.browser.get(events(ana) + "?status=PUBLISHED")
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].flyerUrl").value(endsWith(key)));

		ana.browser.post(event(ana, event) + "/end", "{}").andExpect(jsonPath("$.status").value("ENDED"));
		ana.patch(event(ana, event), "{\"name\":\"Outro nome\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-event-status"));
	}

	@Test
	void rejectsFlyerKeyOutsideTheEventFolder() throws Exception {
		Producer ana = producer();
		String event = createEvent(ana);
		String otherEvent = createEvent(ana);

		ana.put(event(ana, event) + "/media/flyer",
				"{\"key\":\"orgs/" + ana.org + "/events/" + otherEvent + "/flyer/a.jpg\",\"width\":10,\"height\":10}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-media-key"));
		ana.put(event(ana, event) + "/media/flyer",
				"{\"key\":\"orgs/" + ana.org + "/events/" + event + "/flyer/../../x.jpg\",\"width\":10,\"height\":10}")
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void cancelsDraft() throws Exception {
		Producer ana = producer();
		String event = createEvent(ana);

		ana.browser.post(event(ana, event) + "/cancel", "{}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	/** Toda rota responde como "não existe" para evento de outra organização. */
	@Test
	void eventOfAnotherOrganizationLooksLikeItDoesNotExist() throws Exception {
		Producer ana = producer();
		String eventOfAna = createEvent(ana);
		Producer bia = producer();
		String path = "/api/v1/orgs/" + bia.org + "/events/" + eventOfAna;

		bia.browser.get(path).andExpect(status().isNotFound());
		bia.patch(path, "{\"name\":\"Invadido\"}").andExpect(status().isNotFound());
		bia.put(path + "/media/flyer", "{\"key\":\"x.jpg\",\"width\":1,\"height\":1}").andExpect(status().isNotFound());
		bia.browser.post(path + "/publish", "{}").andExpect(status().isNotFound());
		bia.browser.post(path + "/end", "{}").andExpect(status().isNotFound());
		bia.browser.post(path + "/cancel", "{}").andExpect(status().isNotFound());
		bia.browser.get("/api/v1/orgs/" + bia.org + "/events").andExpect(jsonPath("$.items", hasSize(0)));

		// Usando a organização da Ana na URL, a Bia nem é membro.
		bia.browser.get(event(ana, eventOfAna)).andExpect(status().isNotFound());
		ana.browser.get(event(ana, eventOfAna)).andExpect(jsonPath("$.name").value("Calourada"));
	}

	@Test
	void promoterReadsButCannotEditAndManagerCannotCancel() throws Exception {
		Producer ana = producer();
		String event = createEvent(ana);
		Producer promoter = memberOf(ana, "PROMOTER");
		Producer manager = memberOf(ana, "MANAGER");

		promoter.browser.get(event(ana, event)).andExpect(status().isOk());
		promoter.browser.get(events(ana)).andExpect(jsonPath("$.items", hasSize(1)));
		promoter.patch(event(ana, event), "{\"name\":\"Mudei\"}").andExpect(status().isForbidden());
		promoter.browser.post(events(ana), "{\"name\":\"Nova\"}").andExpect(status().isForbidden());

		manager.patch(event(ana, event), "{\"name\":\"Calourada nova\"}").andExpect(status().isOk());
		manager.browser.post(event(ana, event) + "/cancel", "{}").andExpect(status().isForbidden());
	}

	// ---------- apoio ----------

	private record Producer(TestBrowser browser, String org, MockMvc mvc) {

		org.springframework.test.web.servlet.ResultActions patch(String url, String json) throws Exception {
			return browser.send(MockMvcRequestBuilders.patch(url), json);
		}

		org.springframework.test.web.servlet.ResultActions put(String url, String json) throws Exception {
			return browser.send(MockMvcRequestBuilders.put(url), json);
		}

	}

	private Producer producer() throws Exception {
		TestBrowser browser = signup("prod-" + unique() + "@festa.test");
		String json = browser.post("/api/v1/orgs", "{\"name\":\"Atlética " + unique() + "\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return new Producer(browser, JsonPath.read(json, "$.id"), mvc);
	}

	/** Usuário novo colocado direto na organização com o papel (o convite tem teste próprio). */
	private Producer memberOf(Producer owner, String role) throws Exception {
		String email = role.toLowerCase() + "-" + unique() + "@festa.test";
		TestBrowser browser = signup(email);
		UUID userId = jdbc.sql("SELECT id FROM users WHERE email = ?").param(email).query(UUID.class).single();
		jdbc.sql("INSERT INTO organization_members (id, organization_id, user_id, role) VALUES (?, ?, ?, ?)")
			.params(UUID.randomUUID(), UUID.fromString(owner.org), userId, role).update();
		return new Producer(browser, owner.org, mvc);
	}

	private TestBrowser signup(String email) throws Exception {
		TestBrowser browser = new TestBrowser(mvc);
		browser.post("/api/v1/auth/signup",
				"{\"name\":\"Produtor\",\"email\":\"" + email + "\",\"password\":\"senha-forte-123\"}")
			.andExpect(status().isCreated());
		return browser;
	}

	private String createEvent(Producer producer) throws Exception {
		String json = producer.browser.post(events(producer), "{\"name\":\"Calourada\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private static String events(Producer producer) {
		return "/api/v1/orgs/" + producer.org + "/events";
	}

	private static String event(Producer producer, String event) {
		return events(producer) + "/" + event;
	}

	private static String slugOf(String json) {
		return JsonPath.read(json, "$.slug");
	}

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
