package com.festa.shared.db;

import com.festa.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Restrições da migration V001 (users, user_identities, organizations, organization_members). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class IdentityAndOrganizationSchemaTest {

	@Autowired
	JdbcClient jdbc;

	@Test
	void persistsUserOrganizationAndMembership() {
		UUID user = insertUser("ana@festa.com");
		UUID org = insertOrganization("atletica-medicina");

		insertMember(org, user, "OWNER");

		Integer members = jdbc.sql("SELECT count(*) FROM organization_members WHERE organization_id = ?")
			.param(org).query(Integer.class).single();
		assertThat(members).isOne();
	}

	@Test
	void rejectsDuplicateEmail() {
		insertUser("ana@festa.com");

		assertThatThrownBy(() -> insertUser("ana@festa.com")).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void rejectsEmailNotNormalizedToLowercase() {
		assertThatThrownBy(() -> insertUser("Ana@Festa.com")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsDuplicateProviderIdentity() {
		UUID ana = insertUser("ana@festa.com");
		UUID bia = insertUser("bia@festa.com");
		insertIdentity(ana, "google-123");

		assertThatThrownBy(() -> insertIdentity(bia, "google-123")).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void rejectsInvalidSlug() {
		assertThatThrownBy(() -> insertOrganization("Atlética Medicina"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsDuplicateSlug() {
		insertOrganization("atletica-medicina");

		assertThatThrownBy(() -> insertOrganization("atletica-medicina")).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void rejectsUnknownRole() {
		UUID user = insertUser("ana@festa.com");
		UUID org = insertOrganization("atletica-medicina");

		assertThatThrownBy(() -> insertMember(org, user, "SUPERADMIN"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsSameUserTwiceInOrganization() {
		UUID user = insertUser("ana@festa.com");
		UUID org = insertOrganization("atletica-medicina");
		insertMember(org, user, "ADMIN");

		assertThatThrownBy(() -> insertMember(org, user, "MANAGER")).isInstanceOf(DuplicateKeyException.class);
	}

	private UUID insertUser(String email) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO users (id, name, email) VALUES (?, ?, ?)").params(id, "Ana", email).update();
		return id;
	}

	private void insertIdentity(UUID userId, String providerUserId) {
		jdbc.sql("INSERT INTO user_identities (id, user_id, provider, provider_user_id) VALUES (?, ?, 'GOOGLE', ?)")
			.params(UUID.randomUUID(), userId, providerUserId).update();
	}

	private UUID insertOrganization(String slug) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO organizations (id, name, slug) VALUES (?, ?, ?)").params(id, "Atlética", slug).update();
		return id;
	}

	private void insertMember(UUID organizationId, UUID userId, String role) {
		jdbc.sql("INSERT INTO organization_members (id, organization_id, user_id, role) VALUES (?, ?, ?, ?)")
			.params(UUID.randomUUID(), organizationId, userId, role).update();
	}

}
