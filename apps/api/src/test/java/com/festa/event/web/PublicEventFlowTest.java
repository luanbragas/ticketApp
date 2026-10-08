package com.festa.event.web;

import com.festa.TestBrowser;
import com.festa.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Página pública: sem login, só publicado ou encerrado, sem ids internos (BACKEND.md §Públicos). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PublicEventFlowTest {

	private static final Instant STARTS = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

	@Autowired
	MockMvc mvc;

	@Test
	void anyoneSeesPublishedEventWithOrganizerAndNoInternalIds() throws Exception {
		Setup setup = publishedEvent();

		mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/events/" + setup.slug))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", containsString("max-age=60")))
			.andExpect(jsonPath("$.name").value("Calourada"))
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.venueName").value("Galpão 42"))
			.andExpect(jsonPath("$.accentColor").value("#e8262c"))
			.andExpect(jsonPath("$.flyer.height").value(1600))
			.andExpect(jsonPath("$.lineup[0].name").value("DJ Kaio Ramos"))
			.andExpect(jsonPath("$.organizer.name").value(setup.orgName))
			.andExpect(jsonPath("$.id").doesNotExist())
			.andExpect(jsonPath("$.organizer.id").doesNotExist());
	}

	@Test
	void draftAndCancelledLookLikeMissing() throws Exception {
		Setup draft = draftEvent();
		mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/events/" + draft.slug))
			.andExpect(status().isNotFound());

		Setup published = publishedEvent();
		published.browser.post(published.eventPath + "/cancel", "{}").andExpect(status().isOk());
		mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/events/" + published.slug))
			.andExpect(status().isNotFound());
		mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/events/nao-existe-" + unique()))
			.andExpect(status().isNotFound());
	}

	@Test
	void endedEventStaysVisible() throws Exception {
		Setup setup = publishedEvent();
		setup.browser.post(setup.eventPath + "/end", "{}").andExpect(status().isOk());

		mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/events/" + setup.slug))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENDED"));
	}

	// ---------- apoio ----------

	private record Setup(TestBrowser browser, String orgName, String eventPath, String slug) {
	}

	private Setup draftEvent() throws Exception {
		TestBrowser browser = new TestBrowser(mvc);
		browser.post("/api/v1/auth/signup",
				"{\"name\":\"Produtor\",\"email\":\"pub-" + unique() + "@festa.test\",\"password\":\"senha-forte-123\"}")
			.andExpect(status().isCreated());
		String orgName = "Atlética " + unique();
		String org = JsonPath.read(browser.post("/api/v1/orgs", "{\"name\":\"" + orgName + "\"}")
			.andReturn().getResponse().getContentAsString(), "$.id");
		String json = browser.post("/api/v1/orgs/" + org + "/events", "{\"name\":\"Calourada\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(json, "$.id");
		return new Setup(browser, orgName, "/api/v1/orgs/" + org + "/events/" + id, JsonPath.read(json, "$.slug"));
	}

	private Setup publishedEvent() throws Exception {
		Setup setup = draftEvent();
		String[] parts = setup.eventPath.split("/");
		String org = parts[4];
		String id = parts[6];
		setup.browser.send(MockMvcRequestBuilders.patch(setup.eventPath), """
				{"startsAt":"%s","endsAt":"%s","venueName":"Galpão 42","accentColor":"#e8262c",
				 "lineup":[{"name":"DJ Kaio Ramos"}]}""".formatted(STARTS, STARTS.plus(6, ChronoUnit.HOURS)))
			.andExpect(status().isOk());
		setup.browser.send(MockMvcRequestBuilders.put(setup.eventPath + "/media/flyer"),
				"{\"key\":\"orgs/" + org + "/events/" + id + "/flyer/" + UUID.randomUUID() + ".jpg\",\"width\":918,\"height\":1600}")
			.andExpect(status().isOk());
		setup.browser.post(setup.eventPath + "/publish", "{}").andExpect(status().isOk());
		return setup;
	}

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
