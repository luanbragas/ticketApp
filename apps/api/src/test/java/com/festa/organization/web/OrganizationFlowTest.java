package com.festa.organization.web;

import com.festa.TestBrowser;
import com.festa.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criação e leitura de organizações, incluindo isolamento entre organizações (CLAUDE.md regra 3). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrganizationFlowTest {

	@Autowired
	MockMvc mvc;

	@Test
	void creatorBecomesOwnerAndSlugComesFromName() throws Exception {
		TestBrowser ana = loggedInUser();
		String name = "Atlética Medicina " + unique();

		ana.post("/api/v1/orgs", "{\"name\":\"" + name + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value(name))
			.andExpect(jsonPath("$.slug").value(org.hamcrest.Matchers.startsWith("atletica-medicina-")))
			.andExpect(jsonPath("$.role").value("OWNER"));
	}

	@Test
	void sameNameGetsNumberedSlug() throws Exception {
		TestBrowser ana = loggedInUser();
		String name = "Calourada " + unique();

		String first = slugOf(ana.post("/api/v1/orgs", "{\"name\":\"" + name + "\"}").andExpect(status().isCreated()));
		String second = slugOf(ana.post("/api/v1/orgs", "{\"name\":\"" + name + "\"}").andExpect(status().isCreated()));

		assertThat(second).isEqualTo(first + "-2");
	}

	@Test
	void usesRequestedSlugAndRejectsTakenOne() throws Exception {
		String slug = "atletica-" + unique();
		loggedInUser().post("/api/v1/orgs", "{\"name\":\"Atlética\",\"slug\":\"" + slug + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value(slug));

		loggedInUser().post("/api/v1/orgs", "{\"name\":\"Outra\",\"slug\":\"" + slug + "\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/slug-taken"));
	}

	@Test
	void rejectsInvalidSlug() throws Exception {
		loggedInUser().post("/api/v1/orgs", "{\"name\":\"Atlética\",\"slug\":\"Atlética Med\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("slug"));
	}

	@Test
	void requiresLogin() throws Exception {
		TestBrowser anonymous = new TestBrowser(mvc);

		anonymous.post("/api/v1/orgs", "{\"name\":\"Atlética\"}").andExpect(status().isUnauthorized());
		anonymous.get("/api/v1/orgs").andExpect(status().isUnauthorized());
	}

	@Test
	void listsOnlyOrganizationsOfTheUser() throws Exception {
		TestBrowser ana = loggedInUser();
		TestBrowser bia = loggedInUser();
		ana.post("/api/v1/orgs", "{\"name\":\"Da Ana\"}").andExpect(status().isCreated());
		ana.post("/api/v1/orgs", "{\"name\":\"Também da Ana\"}").andExpect(status().isCreated());
		bia.post("/api/v1/orgs", "{\"name\":\"Da Bia\"}").andExpect(status().isCreated());

		ana.get("/api/v1/orgs")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].name").value("Da Ana"))
			.andExpect(jsonPath("$.items[1].name").value("Também da Ana"));
		bia.get("/api/v1/orgs").andExpect(jsonPath("$.items", hasSize(1)));
	}

	@Test
	void memberReadsOwnOrganization() throws Exception {
		TestBrowser ana = loggedInUser();
		String orgId = idOf(ana.post("/api/v1/orgs", "{\"name\":\"Atlética\"}"));

		ana.get("/api/v1/orgs/" + orgId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(orgId))
			.andExpect(jsonPath("$.role").value("OWNER"));
	}

	/** Usuário da organização B não enxerga a A: mesma resposta de uma organização que não existe. */
	@Test
	void organizationOfAnotherTenantLooksLikeItDoesNotExist() throws Exception {
		String orgA = idOf(loggedInUser().post("/api/v1/orgs", "{\"name\":\"Org A\"}"));
		TestBrowser userB = loggedInUser();
		userB.post("/api/v1/orgs", "{\"name\":\"Org B\"}").andExpect(status().isCreated());

		String otherTenant = userB.get("/api/v1/orgs/" + orgA)
			.andExpect(status().isNotFound())
			.andReturn().getResponse().getContentAsString();
		String missing = userB.get("/api/v1/orgs/" + UUID.randomUUID())
			.andExpect(status().isNotFound())
			.andReturn().getResponse().getContentAsString();

		// Tudo igual, exceto "instance", que ecoa a URL pedida.
		for (String field : new String[] { "$.type", "$.title", "$.status", "$.detail" }) {
			Object fromOtherTenant = JsonPath.read(otherTenant, field);
			Object fromMissing = JsonPath.read(missing, field);
			assertThat(fromOtherTenant).as(field).isEqualTo(fromMissing);
		}
	}

	@Test
	void malformedIdIsNotFound() throws Exception {
		loggedInUser().get("/api/v1/orgs/nao-e-uuid")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/not-found"));
	}

	private TestBrowser loggedInUser() throws Exception {
		TestBrowser browser = new TestBrowser(mvc);
		browser.post("/api/v1/auth/signup",
				"{\"name\":\"Produtor\",\"email\":\"prod-" + unique() + "@festa.test\",\"password\":\"senha-forte-123\"}")
			.andExpect(status().isCreated());
		return browser;
	}

	private static String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static String slugOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.slug");
	}

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
