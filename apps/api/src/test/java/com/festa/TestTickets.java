package com.festa;

import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ingresso mínimo para o evento poder ser publicado (publicar exige ingressos). */
public final class TestTickets {

	private TestTickets() {
	}

	/** Cria o tipo "Pista" com um lote de R$ 30,00 e devolve o id do tipo. */
	public static String addPista(TestBrowser browser, String eventPath) throws Exception {
		String json = browser.post(eventPath + "/ticket-types", "{\"name\":\"Pista\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String typeId = JsonPath.read(json, "$.types[-1].id");
		browser.post(eventPath + "/ticket-types/" + typeId + "/batches",
				"{\"name\":\"Lote 1\",\"priceCents\":3000,\"capacity\":100}")
			.andExpect(status().isCreated());
		return typeId;
	}

}
