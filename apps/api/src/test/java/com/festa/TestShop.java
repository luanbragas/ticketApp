package com.festa;

import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Festa publicada pronta para vender: Pista (inteira) e Meia, um lote em cada. */
public final class TestShop {

	public record Shop(TestBrowser producer, String org, String eventId, String eventPath, String slug, String pista,
			String meia) {
	}

	private TestShop() {
	}

	/**
	 * @param pistaCapacity capacidade do Lote 1 da Pista (R$ 30,00); a Meia tem 50 a R$ 15,00
	 * @param maxPerCpf limite por CPF no evento, ou {@code null} para sem limite
	 */
	public static Shop open(MockMvc mvc, int pistaCapacity, Integer maxPerCpf) throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		TestBrowser producer = new TestBrowser(mvc);
		producer.post("/api/v1/auth/signup",
				"{\"name\":\"Produtor\",\"email\":\"shop-" + unique + "@festa.test\",\"password\":\"senha-forte-123\"}")
			.andExpect(status().isCreated());
		String org = JsonPath.read(producer.post("/api/v1/orgs", "{\"name\":\"Atlética " + unique + "\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		String json = producer.post("/api/v1/orgs/" + org + "/events", "{\"name\":\"Calourada " + unique + "\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String eventId = JsonPath.read(json, "$.id");
		String slug = JsonPath.read(json, "$.slug");
		String path = "/api/v1/orgs/" + org + "/events/" + eventId;

		Instant starts = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
		producer.send(MockMvcRequestBuilders.patch(path), """
				{"startsAt":"%s","endsAt":"%s","venueName":"Galpão 42"%s}"""
			.formatted(starts, starts.plus(6, ChronoUnit.HOURS),
					maxPerCpf == null ? "" : ",\"maxTicketsPerCpf\":" + maxPerCpf))
			.andExpect(status().isOk());
		producer.send(MockMvcRequestBuilders.put(path + "/media/flyer"), "{\"key\":\"orgs/" + org + "/events/"
				+ eventId + "/flyer/" + UUID.randomUUID() + ".jpg\",\"width\":918,\"height\":1600}")
			.andExpect(status().isOk());

		String pista = batch(producer, path, "Pista", false, 3000, pistaCapacity);
		String meia = batch(producer, path, "Meia", true, 1500, 50);
		producer.post(path + "/publish", "{}").andExpect(status().isOk());
		return new Shop(producer, org, eventId, path, slug, pista, meia);
	}

	private static String batch(TestBrowser producer, String path, String type, boolean halfPrice, long price,
			int capacity) throws Exception {
		String typeJson = producer.post(path + "/ticket-types",
				"{\"name\":\"" + type + "\",\"halfPrice\":" + halfPrice + "}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String typeId = JsonPath.read(typeJson, "$.types[-1].id");
		String catalog = producer.post(path + "/ticket-types/" + typeId + "/batches",
				"{\"name\":\"Lote 1\",\"priceCents\":" + price + ",\"capacity\":" + capacity + "}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(catalog, "$.types[-1].batches[0].id");
	}

}
