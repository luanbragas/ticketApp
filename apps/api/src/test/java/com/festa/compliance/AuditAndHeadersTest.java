package com.festa.compliance;

import com.festa.TestBrowser;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
import com.festa.TestcontainersConfiguration;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SECURITY.md: audit log das ações sensíveis e headers de segurança da API. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditAndHeadersTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void publishingPriceChangeAndCancellingAreAudited() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);

		shop.producer().send(MockMvcRequestBuilders.put(shop.eventPath() + "/batches/" + shop.pista()),
				"{\"name\":\"Lote 1\",\"priceCents\":3500,\"capacity\":120}")
			.andExpect(status().isOk());
		// Mudar só o nome não é sensível.
		shop.producer().send(MockMvcRequestBuilders.put(shop.eventPath() + "/batches/" + shop.pista()),
				"{\"name\":\"Lote promocional\",\"priceCents\":3500,\"capacity\":120}")
			.andExpect(status().isOk());
		shop.producer().post(shop.eventPath() + "/cancel", "{}").andExpect(status().isOk());

		assertThat(actions(shop.org())).containsExactly("event.published", "batch.terms-changed", "event.cancelled");
		String data = jdbc.sql("SELECT data::text FROM audit_logs WHERE action = 'batch.terms-changed' AND organization_id = ?")
			.param(UUID.fromString(shop.org())).query(String.class).single();
		assertThat(data).contains("3000", "3500", "100", "120");
	}

	@Test
	void apiSendsSecurityHeaders() throws Exception {
		new TestBrowser(mvc).get("/api/v1/public/events/nao-existe")
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("Referrer-Policy", "no-referrer"))
			.andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
	}

	private List<String> actions(String org) {
		return jdbc.sql("SELECT action FROM audit_logs WHERE organization_id = ? ORDER BY created_at, id")
			.param(UUID.fromString(org)).query(String.class).list();
	}

}
