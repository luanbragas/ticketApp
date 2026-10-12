package com.festa.compliance;

import com.festa.TestBrowser;
import com.festa.TestOrders;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
import com.festa.TestcontainersConfiguration;
import com.festa.shared.outbox.OutboxWorker;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SECURITY.md §LGPD: aceite dos termos e da privacidade, com versão, no cadastro e na compra. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsentFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	OutboxWorker outbox;

	@Test
	void currentVersionsArePublic() throws Exception {
		new TestBrowser(mvc).get("/api/v1/public/legal")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].kind", containsInAnyOrder("TERMS", "PRIVACY")))
			.andExpect(jsonPath("$[*].url", containsInAnyOrder("/termos", "/privacidade")));
	}

	@Test
	void signupRequiresAndRecordsConsent() throws Exception {
		String email = "consent-" + UUID.randomUUID().toString().substring(0, 8) + "@festa.test";
		new TestBrowser(mvc).post("/api/v1/auth/signup",
				"{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"senha-forte-123\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field == 'termsAccepted')]").exists());

		new TestBrowser(mvc).send(MockMvcRequestBuilders.post("/api/v1/auth/signup")
				.header("User-Agent", "Teste/1.0").with(r -> {
					r.setRemoteAddr("203.0.113.7");
					return r;
				}), "{\"name\":\"Ana\",\"email\":\"" + email.toUpperCase()
						+ "\",\"password\":\"senha-forte-123\",\"termsAccepted\":true}")
			.andExpect(status().isCreated());

		List<String> rows = jdbc.sql("""
				SELECT v.kind || ':' || v.version || ':' || c.purpose || ':' || c.ip || ':' || c.user_agent
				       || ':' || (c.user_id = u.id)
				  FROM consent_records c
				  JOIN terms_versions v ON v.id = c.terms_version_id
				  JOIN users u ON u.email = c.email
				 WHERE c.email = ?
				 ORDER BY v.kind
				""").param(email).query(String.class).list();
		assertThat(rows).containsExactly("PRIVACY:rascunho-2026-10:SIGNUP:203.0.113.7:Teste/1.0:true",
				"TERMS:rascunho-2026-10:SIGNUP:203.0.113.7:Teste/1.0:true");
	}

	@Test
	void checkoutRecordsConsentForTheOrder() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		UUID order = TestOrders.paid(mvc, outbox, shop, 1, null);

		assertThat(jdbc.sql("""
				SELECT count(*) FROM consent_records
				 WHERE reference_id = ? AND purpose = 'CHECKOUT' AND user_id IS NULL AND email = 'ana@festa.test'
				""").param(order).query(Integer.class).single()).isEqualTo(2);
		assertThat(jdbc.sql("SELECT terms_version FROM orders WHERE id = ?").param(order).query(String.class).single())
			.isEqualTo("rascunho-2026-10");
	}

}
