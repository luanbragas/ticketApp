package com.festa;

import com.jayway.jsonpath.JsonPath;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Conecta a conta de recebimento simulada (publicar exige conta de recebimento). */
public final class TestPayments {

	private TestPayments() {
	}

	/** Faz o OAuth do gateway simulado: pede o endereço, lê code e state e conclui o retorno. */
	public static void connect(TestBrowser owner, String orgId) throws Exception {
		String base = "/api/v1/orgs/" + orgId + "/payment-account";
		String url = JsonPath.read(owner.post(base + "/connect", "{}")
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.authorizationUrl");
		var params = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
		owner.post(base + "/callback",
				"{\"code\":\"" + params.getFirst("code") + "\",\"state\":\"" + params.getFirst("state") + "\"}")
			.andExpect(status().isOk());
	}

}
