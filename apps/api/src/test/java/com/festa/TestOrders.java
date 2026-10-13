package com.festa;

import com.festa.TestShop.Shop;
import com.festa.shared.outbox.OutboxWorker;
import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pedido pago pelo PIX simulado, com os eventos do outbox já entregues (ingressos, promoter, financeiro). */
public final class TestOrders {

	private TestOrders() {
	}

	/** {@code tickets} ingressos da Pista (R$ 30,00 + 10% de taxa cada); {@code promoterCode} pode ser nulo. */
	public static UUID paid(MockMvc mvc, OutboxWorker outbox, Shop shop, int tickets, String promoterCode)
			throws Exception {
		String key = UUID.randomUUID().toString().replace("-", "") + "k";
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < tickets; i++) {
			items.append(i == 0 ? "" : ",").append("{\"batchId\":\"").append(shop.pista())
				.append("\",\"holderName\":\"Titular\",\"holderCpf\":\"").append(TestCpf.random()).append("\"}");
		}
		String promoter = promoterCode == null ? "" : ",\"promoterCode\":\"" + promoterCode + "\"";
		String json = new TestBrowser(mvc).send(MockMvcRequestBuilders.post("/api/v1/public/orders")
				.header("Idempotency-Key", key), """
				{"event":"%s","buyer":{"name":"Ana Souza","email":"ana@festa.test","cpf":"%s"},"tickets":[%s],
				 "adultDeclared":true,"termsAccepted":true%s}""".formatted(shop.slug(), TestCpf.random(), items, promoter))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		UUID order = UUID.fromString(JsonPath.read(json, "$.id"));
		String path = "/api/v1/public/orders/" + order + "/payment";
		new TestBrowser(mvc).send(MockMvcRequestBuilders.post(path).header("X-Order-Key", key), "{\"method\":\"PIX\"}")
			.andExpect(status().isCreated());
		new TestBrowser(mvc).send(MockMvcRequestBuilders.post(path + "/simulate-approval").header("X-Order-Key", key), "")
			.andExpect(status().isNoContent());
		outbox.drain(Integer.MAX_VALUE);
		return order;
	}

}
