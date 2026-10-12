package com.festa.event.web;

import com.festa.TestBrowser;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
import com.festa.TestcontainersConfiguration;
import com.festa.event.app.EventEnding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Evento publicado vira encerrado sozinho depois do horário de término. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventEndingTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	EventEnding ending;

	@Test
	void publishedEventEndsAfterItsEndTime() throws Exception {
		Shop over = TestShop.open(mvc, 10, null);
		Shop upcoming = TestShop.open(mvc, 10, null);
		jdbc.sql("""
				UPDATE events SET starts_at = now() - interval '10 hours', ends_at = now() - interval '1 minute'
				 WHERE id = ?
				""").param(UUID.fromString(over.eventId())).update();

		assertThat(ending.endFinished()).isGreaterThanOrEqualTo(1);
		assertThat(ending.endFinished()).isZero();

		assertThat(status(over)).isEqualTo("ENDED");
		assertThat(status(upcoming)).isEqualTo("PUBLISHED");
		// Encerrado continua com página pública (ingressos e informações), sem venda.
		new TestBrowser(mvc).get("/api/v1/public/events/" + over.slug()).andExpect(jsonPath("$.status").value("ENDED"));
	}

	private String status(Shop shop) {
		return jdbc.sql("SELECT status FROM events WHERE id = ?").param(UUID.fromString(shop.eventId()))
			.query(String.class).single();
	}

}
