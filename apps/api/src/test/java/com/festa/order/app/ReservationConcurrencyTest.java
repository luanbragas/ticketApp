package com.festa.order.app;

import com.festa.TestCpf;
import com.festa.TestShop;
import com.festa.TestShop.Shop;
import com.festa.TestcontainersConfiguration;
import com.festa.order.app.CheckoutService.Buyer;
import com.festa.order.app.CheckoutService.PlaceOrder;
import com.festa.order.app.CheckoutService.TicketRequest;
import com.festa.order.domain.OrderRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pronto quando do M4: 500 compras concorrentes num lote de 100 resultam em exatamente 100 reservas
 * (overselling = 0). Cada compra é um comprador diferente, todos largando juntos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReservationConcurrencyTest {

	private static final int BUYERS = 500;
	private static final int CAPACITY = 100;

	@Autowired
	MockMvc mvc;

	@Autowired
	CheckoutService checkout;

	@Autowired
	JdbcClient jdbc;

	@Test
	void fiveHundredBuyersForOneHundredTickets() throws Exception {
		Shop shop = TestShop.open(mvc, CAPACITY, null);
		UUID batch = UUID.fromString(shop.pista());
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Outcome>> results = new ArrayList<>();

		try (ExecutorService pool = Executors.newFixedThreadPool(64)) {
			for (int i = 0; i < BUYERS; i++) {
				results.add(pool.submit(() -> {
					start.await();
					String cpf = TestCpf.random();
					try {
						checkout.place(new PlaceOrder(shop.slug(), UUID.randomUUID().toString().replace("-", "") + "k",
								new Buyer("Comprador", "c@festa.test", null, cpf),
								List.of(new TicketRequest(batch, "Titular", cpf, null)), true, true, null));
						return Outcome.RESERVED;
					}
					catch (OrderRuleException ex) {
						assertThat(ex.getCode()).isEqualTo("batch-unavailable");
						return Outcome.SOLD_OUT;
					}
				}));
			}
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(2, TimeUnit.MINUTES)).isTrue();
		}

		long reservedOk = 0;
		for (Future<Outcome> result : results) {
			if (result.get() == Outcome.RESERVED) {
				reservedOk++;
			}
		}
		assertThat(reservedOk).isEqualTo(CAPACITY);
		assertThat(jdbc.sql("SELECT reserved FROM ticket_batches WHERE id = ?").param(batch).query(Integer.class).single())
			.isEqualTo(CAPACITY);
		assertThat(jdbc.sql("""
				SELECT count(*) FROM order_items i JOIN orders o ON o.id = i.order_id
				 WHERE i.ticket_batch_id = ? AND o.status = 'PENDING_PAYMENT'""")
			.param(batch).query(Integer.class).single()).isEqualTo(CAPACITY);
	}

	private enum Outcome {
		RESERVED, SOLD_OUT
	}

}
