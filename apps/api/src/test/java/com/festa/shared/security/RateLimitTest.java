package com.festa.shared.security;

import com.festa.TestBrowser;
import com.festa.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rate limit por IP nas rotas de abuso (SECURITY.md checklist "Rate limit ativo nos endpoints listados"). */
@SpringBootTest(properties = "festa.rate-limit.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitTest {

	@Autowired
	MockMvc mvc;

	@Test
	void sixthSignupInAMinuteFromTheSameIpIsRefused() throws Exception {
		TestBrowser browser = new TestBrowser(mvc);
		for (int i = 0; i < 5; i++) {
			browser.send(signup("10.0.0.1"), body()).andExpect(status().isCreated());
		}

		browser.send(signup("10.0.0.1"), body())
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/rate-limited"));

		// Outro IP não paga pelo primeiro.
		browser.send(signup("10.0.0.2"), body()).andExpect(status().isCreated());
	}

	@Test
	void routesOutsideTheListAreNotLimited() throws Exception {
		TestBrowser browser = new TestBrowser(mvc);
		for (int i = 0; i < 30; i++) {
			browser.get("/api/v1/auth/csrf").andExpect(status().isNoContent());
		}
	}

	private static MockHttpServletRequestBuilder signup(String ip) {
		return MockMvcRequestBuilders.post("/api/v1/auth/signup").with(request -> {
			request.setRemoteAddr(ip);
			return request;
		});
	}

	private static String body() {
		return "{\"name\":\"Ana\",\"email\":\"rl-" + UUID.randomUUID().toString().substring(0, 8)
				+ "@festa.test\",\"password\":\"senha-forte-123\"}";
	}

}
