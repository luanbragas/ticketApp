package com.festa;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Simula o navegador sobre o MockMvc: guarda cookies entre requisições e envia o token CSRF
 * no header, como a web fará (ADR-001). Busca /auth/csrf uma única vez; depois depende do
 * cookie XSRF-TOKEN que a API devolve.
 */
public class TestBrowser {

	private final MockMvc mvc;
	private final Map<String, Cookie> cookies = new LinkedHashMap<>();
	private boolean csrfFetched;

	public TestBrowser(MockMvc mvc) {
		this.mvc = mvc;
	}

	public ResultActions get(String url) throws Exception {
		return perform(MockMvcRequestBuilders.get(url));
	}

	public ResultActions post(String url, String json) throws Exception {
		return send(MockMvcRequestBuilders.post(url), json);
	}

	/** Escrita com corpo JSON (POST, PUT, PATCH...) já com o token CSRF. */
	public ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
		if (!csrfFetched) {
			get("/api/v1/auth/csrf").andExpect(status().isNoContent());
			csrfFetched = true;
		}
		Cookie csrf = cookies.get("XSRF-TOKEN");
		assertThat(csrf).as("cookie XSRF-TOKEN presente antes da escrita").isNotNull();
		return perform(request
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.header("X-XSRF-TOKEN", csrf.getValue()));
	}

	public Cookie cookie(String name) {
		return cookies.get(name);
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		if (!cookies.isEmpty()) {
			request.cookie(cookies.values().toArray(Cookie[]::new));
		}
		ResultActions actions = mvc.perform(request);
		for (Cookie cookie : actions.andReturn().getResponse().getCookies()) {
			if (cookie.getMaxAge() == 0 || cookie.getValue() == null || cookie.getValue().isEmpty()) {
				cookies.remove(cookie.getName());
			}
			else {
				cookies.put(cookie.getName(), cookie);
			}
		}
		return actions;
	}

}
