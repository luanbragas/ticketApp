package com.festa.identity.web;

import com.festa.TestBrowser;
import com.festa.TestcontainersConfiguration;
import com.festa.identity.domain.MagicLinkToken;
import com.festa.notification.RecordingEmailSender;
import com.festa.notification.api.EmailMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Link mágico de ponta a ponta (SECURITY.md §Autenticação, ADR-004). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, RecordingEmailSender.Config.class })
class MagicLinkFlowTest {

	private static final Pattern LINK = Pattern.compile("http://localhost:3000/link-magico#token=([A-Za-z0-9_-]{43})");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	RecordingEmailSender emails;

	@Test
	void requestSendsLinkByEmailAndStoresOnlyItsHash() throws Exception {
		String email = uniqueEmail();

		requestLink(email);

		List<EmailMessage> sent = emails.sentTo(email);
		assertThat(sent).hasSize(1);
		String token = tokenFrom(sent.getFirst());
		String storedHash = jdbc.sql("SELECT token_hash FROM magic_links WHERE email = ?").param(email)
			.query(String.class).single();
		assertThat(storedHash).isEqualTo(MagicLinkToken.hash(token)).isNotEqualTo(token);
	}

	@Test
	void requestNormalizesEmail() throws Exception {
		String email = uniqueEmail();

		requestLink(email.toUpperCase());

		assertThat(emails.sentTo(email)).hasSize(1);
	}

	@Test
	void firstAccessCreatesVerifiedAccountAndStartsSession() throws Exception {
		String email = uniqueEmail();
		requestLink(email);
		TestBrowser buyer = new TestBrowser(mvc);

		buyer.post("/api/v1/auth/magic-link/consume", consume(lastToken(email)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.emailVerified").value(true))
			.andExpect(jsonPath("$.name").isEmpty());

		buyer.get("/api/v1/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
		assertThat(countUsers(email)).isOne();
	}

	@Test
	void nextAccessReusesTheSameAccount() throws Exception {
		String email = uniqueEmail();
		requestLink(email);
		String firstId = new TestBrowser(mvc).post("/api/v1/auth/magic-link/consume", consume(lastToken(email)))
			.andReturn().getResponse().getContentAsString();

		requestLink(email);
		String secondId = new TestBrowser(mvc).post("/api/v1/auth/magic-link/consume", consume(lastToken(email)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(secondId).isEqualTo(firstId);
		assertThat(countUsers(email)).isOne();
	}

	@Test
	void linkWorksOnlyOnce() throws Exception {
		String email = uniqueEmail();
		requestLink(email);
		String token = lastToken(email);
		new TestBrowser(mvc).post("/api/v1/auth/magic-link/consume", consume(token)).andExpect(status().isOk());

		new TestBrowser(mvc).post("/api/v1/auth/magic-link/consume", consume(token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-magic-link"));
	}

	@Test
	void expiredLinkIsRejected() throws Exception {
		String email = uniqueEmail();
		String token = MagicLinkToken.generate();
		Instant past = Instant.now().minusSeconds(3600);
		jdbc.sql("INSERT INTO magic_links (id, email, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?, ?)")
			.params(UUID.randomUUID(), email, MagicLinkToken.hash(token), Timestamp.from(past.plusSeconds(900)),
				Timestamp.from(past))
			.update();

		new TestBrowser(mvc).post("/api/v1/auth/magic-link/consume", consume(token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-magic-link"));
		assertThat(countUsers(email)).isZero();
	}

	@Test
	void unknownTokenIsRejected() throws Exception {
		new TestBrowser(mvc).post("/api/v1/auth/magic-link/consume", consume(MagicLinkToken.generate()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void limitsLinksPerEmailWithoutTellingTheRequester() throws Exception {
		String email = uniqueEmail();

		for (int i = 0; i < 7; i++) {
			requestLink(email);
		}

		assertThat(emails.sentTo(email)).hasSize(5);
	}

	@Test
	void rejectsInvalidEmail() throws Exception {
		new TestBrowser(mvc).post("/api/v1/public/magic-links", "{\"email\":\"nao-e-email\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("email"));
	}

	/**
	 * Alguém cadastra o e-mail de outra pessoa com uma senha própria. Quando o dono real entra
	 * pelo link mágico, a senha do intruso deixa de valer e a sessão dele cai.
	 */
	@Test
	void ownerTakesOverAccountPreCreatedByAnotherPerson() throws Exception {
		String email = uniqueEmail();
		TestBrowser intruder = new TestBrowser(mvc);
		intruder.post("/api/v1/auth/signup",
				"{\"name\":\"Intruso\",\"email\":\"" + email + "\",\"password\":\"senha-do-intruso\"}")
			.andExpect(status().isCreated());

		requestLink(email);
		TestBrowser owner = new TestBrowser(mvc);
		owner.post("/api/v1/auth/magic-link/consume", consume(lastToken(email)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailVerified").value(true));

		intruder.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
		new TestBrowser(mvc).post("/api/v1/auth/login",
				"{\"email\":\"" + email + "\",\"password\":\"senha-do-intruso\"}")
			.andExpect(status().isUnauthorized());
		owner.get("/api/v1/auth/me").andExpect(status().isOk());
		assertThat(countUsers(email)).isOne();
	}

	private void requestLink(String email) throws Exception {
		new TestBrowser(mvc).post("/api/v1/public/magic-links", "{\"email\":\"" + email + "\"}")
			.andExpect(status().isAccepted());
	}

	private String lastToken(String email) {
		return tokenFrom(emails.sentTo(email).getLast());
	}

	private static String tokenFrom(EmailMessage message) {
		Matcher matcher = LINK.matcher(message.text());
		assertThat(matcher.find()).as("link no e-mail").isTrue();
		return matcher.group(1);
	}

	private int countUsers(String email) {
		return jdbc.sql("SELECT count(*) FROM users WHERE email = ?").param(email).query(Integer.class).single();
	}

	private static String consume(String token) {
		return "{\"token\":\"" + token + "\"}";
	}

	private static String uniqueEmail() {
		return "buyer-" + UUID.randomUUID() + "@festa.test";
	}

}
