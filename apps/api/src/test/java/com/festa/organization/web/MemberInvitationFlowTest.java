package com.festa.organization.web;

import com.festa.TestBrowser;
import com.festa.TestcontainersConfiguration;
import com.festa.notification.RecordingEmailSender;
import com.festa.notification.api.EmailMessage;
import com.festa.shared.security.SecureToken;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Convite de membros de ponta a ponta (PLAN M1: "convida um membro"). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, RecordingEmailSender.Config.class })
class MemberInvitationFlowTest {

	private static final Pattern LINK = Pattern.compile("http://localhost:3000/convite#token=([A-Za-z0-9_-]{43})");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	RecordingEmailSender emails;

	@Test
	void ownerInvitesAndInviteeJoinsWithInvitedRole() throws Exception {
		TestBrowser owner = user("Ana Dona", uniqueEmail());
		String orgId = createOrg(owner, "Atlética Convites");
		String bia = uniqueEmail();

		owner.post(members(orgId), invite(bia, "PROMOTER"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value(bia))
			.andExpect(jsonPath("$.role").value("PROMOTER"));

		EmailMessage email = emails.sentTo(bia).getLast();
		assertThat(email.subject()).isEqualTo("Convite para Atlética Convites no FESTA");
		assertThat(email.text()).contains("Ana Dona convidou você").contains("como Promoter");

		TestBrowser invitee = user("Bia", bia);
		invitee.post("/api/v1/invitations/accept", token(tokenFrom(email)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(orgId))
			.andExpect(jsonPath("$.role").value("PROMOTER"));

		invitee.get("/api/v1/orgs/" + orgId).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PROMOTER"));
	}

	@Test
	void previewShowsOrganizationAndRoleWithoutLogin() throws Exception {
		TestBrowser owner = user("Ana", uniqueEmail());
		String orgId = createOrg(owner, "Atlética Preview");
		String bia = uniqueEmail();
		owner.post(members(orgId), invite(bia, "CHECKIN_OPERATOR")).andExpect(status().isCreated());

		new TestBrowser(mvc).post("/api/v1/public/invitations/preview", token(lastToken(bia)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.organizationName").value("Atlética Preview"))
			.andExpect(jsonPath("$.email").value(bia))
			.andExpect(jsonPath("$.roleLabel").value("Operador de check-in"));
	}

	@Test
	void adminInvitesStaffButNotAdmins() throws Exception {
		TestBrowser owner = user("Ana", uniqueEmail());
		String orgId = createOrg(owner, "Atlética Admin");
		TestBrowser admin = member(owner, orgId, "ADMIN");

		admin.post(members(orgId), invite(uniqueEmail(), "MANAGER")).andExpect(status().isCreated());
		admin.post(members(orgId), invite(uniqueEmail(), "ADMIN"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/role-not-allowed"));
	}

	@Test
	void nobodyInvitesAnOwner() throws Exception {
		TestBrowser owner = user("Ana", uniqueEmail());
		String orgId = createOrg(owner, "Atlética Dono");

		owner.post(members(orgId), invite(uniqueEmail(), "OWNER")).andExpect(status().isForbidden());
	}

	@Test
	void promoterCannotInviteNorSeeTheTeam() throws Exception {
		TestBrowser owner = user("Ana", uniqueEmail());
		String orgId = createOrg(owner, "Atlética Promoter");
		TestBrowser promoter = member(owner, orgId, "PROMOTER");

		promoter.post(members(orgId), invite(uniqueEmail(), "PROMOTER")).andExpect(status().isForbidden());
		promoter.get(members(orgId)).andExpect(status().isForbidden());
	}

	@Test
	void outsiderCannotInviteIntoAnotherOrganization() throws Exception {
		String orgId = createOrg(user("Ana", uniqueEmail()), "Atlética Alheia");
		TestBrowser outsider = user("Intruso", uniqueEmail());

		outsider.post(members(orgId), invite(uniqueEmail(), "ADMIN")).andExpect(status().isNotFound());
		outsider.get(members(orgId)).andExpect(status().isNotFound());
	}

	@Test
	void ownerSeesMembersAndPendingInvitations() throws Exception {
		TestBrowser owner = user("Ana Dona", uniqueEmail());
		String orgId = createOrg(owner, "Atlética Equipe");
		member(owner, orgId, "MANAGER");
		String pending = uniqueEmail();
		owner.post(members(orgId), invite(pending, "PROMOTER")).andExpect(status().isCreated());

		owner.get(members(orgId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.members", hasSize(2)))
			.andExpect(jsonPath("$.members[0].name").value("Ana Dona"))
			.andExpect(jsonPath("$.members[0].role").value("OWNER"))
			.andExpect(jsonPath("$.members[1].role").value("MANAGER"))
			.andExpect(jsonPath("$.pendingInvitations", hasSize(1)))
			.andExpect(jsonPath("$.pendingInvitations[0].email").value(pending));
	}

	@Test
	void invitationCannotBeAcceptedByAnotherAccount() throws Exception {
		TestBrowser owner = user("Ana", uniqueEmail());
		String orgId = createOrg(owner, "Atlética E-mail");
		String bia = uniqueEmail();
		owner.post(members(orgId), invite(bia, "ADMIN")).andExpect(status().isCreated());

		user("Outra pessoa", uniqueEmail()).post("/api/v1/invitations/accept", token(lastToken(bia)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/invalid-invitation"));
	}

	@Test
	void invitationWorksOnlyOnce() throws Exception {
		TestBrowser owner = user("Ana", uniqueEmail());
		String orgId = createOrg(owner, "Atlética Uma Vez");
		String bia = uniqueEmail();
		owner.post(members(orgId), invite(bia, "MANAGER")).andExpect(status().isCreated());
		String invitationToken = lastToken(bia);
		TestBrowser invitee = user("Bia", bia);
		invitee.post("/api/v1/invitations/accept", token(invitationToken)).andExpect(status().isOk());

		invitee.post("/api/v1/invitations/accept", token(invitationToken)).andExpect(status().isUnprocessableContent());
	}

	@Test
	void expiredInvitationIsRejected() throws Exception {
		TestBrowser owner = user("Ana", uniqueEmail());
		String orgId = createOrg(owner, "Atlética Expirada");
		String bia = uniqueEmail();
		String expiredToken = SecureToken.generate();
		UUID ownerId = UUID.fromString(JsonPath.read(owner.get("/api/v1/auth/me").andReturn().getResponse()
			.getContentAsString(), "$.id"));
		jdbc.sql("""
				INSERT INTO organization_invitations (id, organization_id, email, role, token_hash, invited_by_user_id, expires_at)
				VALUES (?, ?, ?, 'PROMOTER', ?, ?, ?)
				""")
			.params(UUID.randomUUID(), UUID.fromString(orgId), bia, SecureToken.hash(expiredToken), ownerId,
				Timestamp.from(Instant.now().minusSeconds(60)))
			.update();

		user("Bia", bia).post("/api/v1/invitations/accept", token(expiredToken))
			.andExpect(status().isUnprocessableContent());
		new TestBrowser(mvc).post("/api/v1/public/invitations/preview", token(expiredToken))
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void invitingExistingMemberIsConflict() throws Exception {
		String anaEmail = uniqueEmail();
		TestBrowser owner = user("Ana", anaEmail);
		String orgId = createOrg(owner, "Atlética Repetida");

		owner.post(members(orgId), invite(anaEmail.toUpperCase(), "ADMIN"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("https://festa.com/errors/already-member"));
	}

	@Test
	void acceptingRequiresLogin() throws Exception {
		new TestBrowser(mvc).post("/api/v1/invitations/accept", token(SecureToken.generate()))
			.andExpect(status().isUnauthorized());
	}

	/** Convida e faz a pessoa aceitar, devolvendo o navegador dela já logado. */
	private TestBrowser member(TestBrowser owner, String orgId, String role) throws Exception {
		String email = uniqueEmail();
		owner.post(members(orgId), invite(email, role)).andExpect(status().isCreated());
		TestBrowser invitee = user("Membro " + role, email);
		invitee.post("/api/v1/invitations/accept", token(lastToken(email))).andExpect(status().isOk());
		return invitee;
	}

	private TestBrowser user(String name, String email) throws Exception {
		TestBrowser browser = new TestBrowser(mvc);
		browser.post("/api/v1/auth/signup",
				"{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"senha-forte-123\"}".formatted(name, email))
			.andExpect(status().isCreated());
		return browser;
	}

	private static String createOrg(TestBrowser owner, String name) throws Exception {
		return JsonPath.read(owner.post("/api/v1/orgs", "{\"name\":\"" + name + "\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String lastToken(String email) {
		return tokenFrom(emails.sentTo(email).getLast());
	}

	private static String tokenFrom(EmailMessage message) {
		Matcher matcher = LINK.matcher(message.text());
		assertThat(matcher.find()).as("link do convite no e-mail").isTrue();
		return matcher.group(1);
	}

	private static String members(String orgId) {
		return "/api/v1/orgs/" + orgId + "/members";
	}

	private static String invite(String email, String role) {
		return "{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role);
	}

	private static String token(String token) {
		return "{\"token\":\"" + token + "\"}";
	}

	private static String uniqueEmail() {
		return "membro-" + UUID.randomUUID() + "@festa.test";
	}

}
