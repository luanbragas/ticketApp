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

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** URL pré-assinada do flyer: tipo, tamanho e isolamento por organização (SECURITY.md §Upload). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MediaUploadFlowTest {

	private static final String FIVE_MB = String.valueOf(5L * 1024 * 1024);

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void ownerGetsSignedUploadUrlWithServerGeneratedName() throws Exception {
		TestBrowser ana = loggedInUser();
		String org = createOrganization(ana);
		UUID event = insertEvent(org);

		ana.post(url(org, event), body("FLYER", "image/jpeg", FIVE_MB))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.method").value("PUT"))
			.andExpect(jsonPath("$.uploadUrl").value(containsString("X-Amz-Signature=")))
			.andExpect(jsonPath("$.headers['Content-Type']").value("image/jpeg"))
			.andExpect(jsonPath("$.key").value(startsWith("orgs/" + org + "/events/" + event + "/flyer/")))
			.andExpect(jsonPath("$.key").value(endsWith(".jpg")))
			.andExpect(jsonPath("$.publicUrl").value(startsWith("http://localhost:9090/festa-media/orgs/")))
			.andExpect(jsonPath("$.expiresAt").exists());
	}

	@Test
	void rejectsFormatOutsideJpegPngWebp() throws Exception {
		TestBrowser ana = loggedInUser();
		String org = createOrganization(ana);
		UUID event = insertEvent(org);

		ana.post(url(org, event), body("FLYER", "image/gif", "1000"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("contentType"));
	}

	@Test
	void rejectsImageOver5Mb() throws Exception {
		TestBrowser ana = loggedInUser();
		String org = createOrganization(ana);
		UUID event = insertEvent(org);

		ana.post(url(org, event), body("FLYER", "image/png", String.valueOf(5L * 1024 * 1024 + 1)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("size"));
	}

	@Test
	void eventOfAnotherOrganizationLooksLikeItDoesNotExist() throws Exception {
		TestBrowser ana = loggedInUser();
		UUID eventOfAna = insertEvent(createOrganization(ana));
		TestBrowser bia = loggedInUser();
		String orgOfBia = createOrganization(bia);

		// Bia é dona da própria organização, mas o evento é da Ana.
		bia.post(url(orgOfBia, eventOfAna), body("FLYER", "image/jpeg", "1000"))
			.andExpect(status().isNotFound());
	}

	@Test
	void nonMemberCannotAskForUploadUrl() throws Exception {
		TestBrowser ana = loggedInUser();
		String org = createOrganization(ana);
		UUID event = insertEvent(org);

		loggedInUser().post(url(org, event), body("FLYER", "image/jpeg", "1000"))
			.andExpect(status().isNotFound());
	}

	@Test
	void requiresLogin() throws Exception {
		new TestBrowser(mvc).post(url(UUID.randomUUID().toString(), UUID.randomUUID()), body("FLYER", "image/jpeg", "1000"))
			.andExpect(status().isUnauthorized());
	}

	private static String url(String org, UUID event) {
		return "/api/v1/orgs/" + org + "/events/" + event + "/media/upload-url";
	}

	private static String body(String kind, String contentType, String size) {
		return "{\"kind\":\"" + kind + "\",\"contentType\":\"" + contentType + "\",\"size\":" + size + "}";
	}

	private UUID insertEvent(String org) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO events (id, organization_id, slug, name) VALUES (?, ?, ?, 'Calourada')")
			.params(id, UUID.fromString(org), "calourada-" + unique()).update();
		return id;
	}

	private String createOrganization(TestBrowser browser) throws Exception {
		String json = browser.post("/api/v1/orgs", "{\"name\":\"Atlética " + unique() + "\"}")
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private TestBrowser loggedInUser() throws Exception {
		TestBrowser browser = new TestBrowser(mvc);
		browser.post("/api/v1/auth/signup",
				"{\"name\":\"Produtor\",\"email\":\"prod-" + unique() + "@festa.test\",\"password\":\"senha-forte-123\"}")
			.andExpect(status().isCreated());
		return browser;
	}

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
