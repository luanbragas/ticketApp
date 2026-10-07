package com.festa.identity.web;

import com.festa.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cadastro, login, sessão e logout de ponta a ponta, com cookies e CSRF como no navegador (ADR-001). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	Browser browser;

	@BeforeEach
	void newBrowser() {
		browser = new Browser();
	}

	@Test
	void signupCreatesAccountAndStartsSession() throws Exception {
		String email = uniqueEmail();

		browser.post("/api/v1/auth/signup", signup("Ana Souza", email, "senha-forte-123"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Ana Souza"))
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.emailVerified").value(false));

		browser.get("/api/v1/auth/me")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void sessionCookieIsHttpOnlyAndLax() throws Exception {
		MvcResult result = browser.post("/api/v1/auth/signup", signup("Ana", uniqueEmail(), "senha-forte-123"))
			.andExpect(status().isCreated())
			.andReturn();

		String setCookie = String.join("\n", result.getResponse().getHeaders("Set-Cookie"));
		assertThat(setCookie).contains("festa_session=").contains("HttpOnly").contains("SameSite=Lax");
	}

	@Test
	void storesPasswordAsArgon2idHash() throws Exception {
		String email = uniqueEmail();
		browser.post("/api/v1/auth/signup", signup("Ana", email, "senha-forte-123")).andExpect(status().isCreated());

		String hash = jdbc.sql("SELECT password_hash FROM users WHERE email = ?").param(email)
			.query(String.class).single();
		assertThat(hash).startsWith("$argon2id$").doesNotContain("senha-forte-123");
	}

	@Test
	void normalizesEmailOnSignupAndLogin() throws Exception {
		String email = uniqueEmail();
		browser.post("/api/v1/auth/signup", signup("Ana", email.toUpperCase(), "senha-forte-123"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value(email));

		new Browser().post("/api/v1/auth/login", login(email.toUpperCase(), "senha-forte-123"))
			.andExpect(status().isOk());
	}

	@Test
	void rejectsDuplicateEmailWithConflict() throws Exception {
		String email = uniqueEmail();
		browser.post("/api/v1/auth/signup", signup("Ana", email, "senha-forte-123")).andExpect(status().isCreated());

		new Browser().post("/api/v1/auth/signup", signup("Outra Ana", email, "outra-senha-456"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/email-already-registered"));
	}

	@Test
	void rejectsInvalidSignupWithFieldErrors() throws Exception {
		browser.post("/api/v1/auth/signup", signup("", "nao-e-email", "curta"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/validation"))
			.andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'email')].message").value("E-mail inválido."))
			.andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
	}

	@Test
	void rejectsUnknownFields() throws Exception {
		browser.post("/api/v1/auth/signup",
				"{\"name\":\"Ana\",\"email\":\"" + uniqueEmail() + "\",\"password\":\"senha-forte-123\",\"role\":\"ADMIN\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/malformed-request"));
	}

	@Test
	void loginWithCorrectPasswordStartsSession() throws Exception {
		String email = uniqueEmail();
		new Browser().post("/api/v1/auth/signup", signup("Ana", email, "senha-forte-123"))
			.andExpect(status().isCreated());

		browser.post("/api/v1/auth/login", login(email, "senha-forte-123"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email));
		browser.get("/api/v1/auth/me").andExpect(status().isOk());
	}

	@Test
	void loginWithWrongPasswordOrUnknownEmailReturnsSameError() throws Exception {
		String email = uniqueEmail();
		new Browser().post("/api/v1/auth/signup", signup("Ana", email, "senha-forte-123"))
			.andExpect(status().isCreated());

		browser.post("/api/v1/auth/login", login(email, "senha-errada-000"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-credentials"));
		browser.post("/api/v1/auth/login", login(uniqueEmail(), "senha-forte-123"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-credentials"));
		browser.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
	}

	@Test
	void meWithoutSessionReturnsProblemDetails() throws Exception {
		browser.get("/api/v1/auth/me")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/unauthenticated"))
			.andExpect(jsonPath("$.title").value("Não autenticado"));
	}

	@Test
	void logoutEndsSession() throws Exception {
		browser.post("/api/v1/auth/signup", signup("Ana", uniqueEmail(), "senha-forte-123"))
			.andExpect(status().isCreated());

		browser.post("/api/v1/auth/logout", "").andExpect(status().isNoContent());

		browser.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
	}

	@Test
	void logoutInvalidatesSessionOnServerEvenIfCookieIsReused() throws Exception {
		browser.post("/api/v1/auth/signup", signup("Ana", uniqueEmail(), "senha-forte-123"))
			.andExpect(status().isCreated());
		Cookie stolenSession = browser.cookie("festa_session");

		browser.post("/api/v1/auth/logout", "").andExpect(status().isNoContent());

		mvc.perform(get("/api/v1/auth/me").cookie(stolenSession)).andExpect(status().isUnauthorized());
	}

	@Test
	void signupRotatesCsrfTokenAndReturnsTheNewOne() throws Exception {
		browser.get("/api/v1/auth/csrf");
		String before = browser.cookie("XSRF-TOKEN").getValue();

		browser.post("/api/v1/auth/signup", signup("Ana", uniqueEmail(), "senha-forte-123"))
			.andExpect(status().isCreated());

		assertThat(browser.cookie("XSRF-TOKEN")).isNotNull();
		assertThat(browser.cookie("XSRF-TOKEN").getValue()).isNotEqualTo(before);
	}

	@Test
	void writesWithoutCsrfTokenAreRejected() throws Exception {
		mvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signup("Ana", uniqueEmail(), "senha-forte-123")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/csrf"));
	}

	@Test
	void loginRotatesSessionId() throws Exception {
		String email = uniqueEmail();
		browser.post("/api/v1/auth/signup", signup("Ana", email, "senha-forte-123")).andExpect(status().isCreated());
		String before = browser.cookie("festa_session").getValue();

		browser.post("/api/v1/auth/login", login(email, "senha-forte-123")).andExpect(status().isOk());

		assertThat(browser.cookie("festa_session").getValue()).isNotEqualTo(before);
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@festa.test";
	}

	private static String signup(String name, String email, String password) {
		return "{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, password);
	}

	private static String login(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
	}

	/** Guarda cookies entre requisições e envia o token CSRF no header, como a web fará. */
	class Browser {

		private final Map<String, Cookie> cookies = new LinkedHashMap<>();
		private boolean csrfFetched;

		ResultActions get(String url) throws Exception {
			return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url));
		}

		/** Busca o token CSRF só uma vez, como a web ao abrir; depois depende do cookie que a API devolve. */
		ResultActions post(String url, String json) throws Exception {
			if (!csrfFetched) {
				get("/api/v1/auth/csrf").andExpect(status().isNoContent());
				csrfFetched = true;
			}
			Cookie csrf = cookies.get("XSRF-TOKEN");
			assertThat(csrf).as("cookie XSRF-TOKEN presente antes de " + url).isNotNull();
			return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json)
				.header("X-XSRF-TOKEN", csrf.getValue()));
		}

		Cookie cookie(String name) {
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

}
