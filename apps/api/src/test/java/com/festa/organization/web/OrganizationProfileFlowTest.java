package com.festa.organization.web;

import com.festa.TestBrowser;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
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

import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Perfil público da organização: nome, Instagram, WhatsApp e logo (PLAN.md M1). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrganizationProfileFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void ownerUpdatesProfileWithNormalizedContacts() throws Exception {
		Shop shop = TestShop.open(mvc, 10, null);
		String org = "/api/v1/orgs/" + shop.org();

		patch(shop.producer(), org, "{\"instagram\":\"https://www.instagram.com/atletica.med/\",\"whatsapp\":\"(31) 99999-0000\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.instagram").value("atletica.med"))
			.andExpect(jsonPath("$.whatsapp").value("31999990000"));
		patch(shop.producer(), org, "{\"instagram\":\"@atletica_med\",\"name\":\"Atlética de Medicina\"}")
			.andExpect(jsonPath("$.instagram").value("atletica_med"))
			.andExpect(jsonPath("$.name").value("Atlética de Medicina"))
			.andExpect(jsonPath("$.whatsapp").value("31999990000"));
		patch(shop.producer(), org, "{\"instagram\":\"\"}").andExpect(jsonPath("$.instagram").value(nullValue()));
		patch(shop.producer(), org, "{\"instagram\":\"não vale\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-profile"));
		patch(shop.producer(), org, "{\"whatsapp\":\"123\"}").andExpect(status().isUnprocessableContent());

		// A página pública mostra quem produz.
		new TestBrowser(mvc).get("/api/v1/public/events/" + shop.slug())
			.andExpect(jsonPath("$.organizer.name").value("Atlética de Medicina"));
	}

	@Test
	void logoGoesStraightToTheBucketAndOnlyFromItsOwnFolder() throws Exception {
		Shop shop = TestShop.open(mvc, 10, null);
		Shop other = TestShop.open(mvc, 10, null);
		String org = "/api/v1/orgs/" + shop.org();

		String json = shop.producer().post(org + "/logo/upload-url", "{\"contentType\":\"image/png\",\"size\":20000}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.key").value(startsWith("orgs/" + shop.org() + "/logo/")))
			.andExpect(jsonPath("$.method").value("PUT"))
			.andReturn().getResponse().getContentAsString();
		String key = JsonPath.read(json, "$.key");
		shop.producer().post(org + "/logo/upload-url", "{\"contentType\":\"application/pdf\",\"size\":20000}")
			.andExpect(status().isBadRequest());

		put(shop.producer(), org + "/logo", "{\"key\":\"" + key + "\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.logoUrl").value(endsWith(key)));
		put(shop.producer(), org + "/logo", "{\"key\":\"orgs/" + other.org() + "/logo/" + UUID.randomUUID() + ".png\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-media-key"));
		new TestBrowser(mvc).get("/api/v1/public/events/" + shop.slug())
			.andExpect(jsonPath("$.organizer.logoUrl").value(endsWith(key)));

		put(shop.producer(), org + "/logo", "{\"key\":null}").andExpect(jsonPath("$.logoUrl").value(nullValue()));
	}

	@Test
	void onlyOwnerAndAdminEditTheProfile() throws Exception {
		Shop shop = TestShop.open(mvc, 10, null);
		TestBrowser manager = member(shop, "MANAGER");

		patch(manager, "/api/v1/orgs/" + shop.org(), "{\"instagram\":\"invasor\"}").andExpect(status().isForbidden());
		manager.post("/api/v1/orgs/" + shop.org() + "/logo/upload-url", "{\"contentType\":\"image/png\",\"size\":1}")
			.andExpect(status().isForbidden());
	}

	private org.springframework.test.web.servlet.ResultActions patch(TestBrowser browser, String url, String json)
			throws Exception {
		return browser.send(MockMvcRequestBuilders.patch(url), json);
	}

	private org.springframework.test.web.servlet.ResultActions put(TestBrowser browser, String url, String json)
			throws Exception {
		return browser.send(MockMvcRequestBuilders.put(url), json);
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
