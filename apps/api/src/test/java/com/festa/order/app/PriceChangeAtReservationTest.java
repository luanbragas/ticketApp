package com.festa.order.app;

import com.festa.TestBrowser;
import com.festa.TestCpf;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
import com.festa.TestcontainersConfiguration;
import com.festa.ticketing.api.Inventory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A reserva acontece no fim da transação do pedido (para travar o lote o mínimo possível). Se o preço mudou entre o
 * resumo e a reserva, o pedido não nasce e nada fica reservado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PriceChangeAtReservationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PlatformTransactionManager transactions;

	@MockitoSpyBean
	Inventory inventory;

	@Test
	void priceChangedBeforeReservationRollsEverythingBack() throws Exception {
		Shop shop = TestShop.open(mvc, 100, null);
		// O produtor mudou o preço no instante entre o resumo e a reserva. (O stub passa pelo proxy
		// transacional do Inventory, que exige transação.)
		new TransactionTemplate(transactions).executeWithoutResult(tx -> doAnswer(call -> {
			OptionalLong real = (OptionalLong) call.callRealMethod();
			return real.isPresent() ? OptionalLong.of(real.getAsLong() + 500) : real;
		}).when(inventory).reserve(any(UUID.class), any(UUID.class), anyInt(), any(Instant.class)));
		String email = "preco-" + UUID.randomUUID().toString().substring(0, 8) + "@festa.test";

		new TestBrowser(mvc).send(MockMvcRequestBuilders.post("/api/v1/public/orders")
				.header("Idempotency-Key", UUID.randomUUID().toString().replace("-", "") + "k"), """
				{"event":"%s","buyer":{"name":"Ana","email":"%s","cpf":"%s"},
				 "tickets":[{"batchId":"%s","holderName":"Ana","holderCpf":"%s"}],
				 "adultDeclared":true,"termsAccepted":true}""".formatted(shop.slug(), email, TestCpf.random(),
				shop.pista(), TestCpf.random()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/price-changed"));

		assertThat(jdbc.sql("SELECT reserved FROM ticket_batches WHERE id = ?").param(UUID.fromString(shop.pista()))
			.query(Integer.class).single()).isZero();
		assertThat(jdbc.sql("SELECT count(*) FROM orders WHERE buyer_email = ?").param(email).query(Integer.class)
			.single()).isZero();
		assertThat(jdbc.sql("SELECT count(*) FROM consent_records WHERE email = ?").param(email).query(Integer.class)
			.single()).isZero();
	}

}
