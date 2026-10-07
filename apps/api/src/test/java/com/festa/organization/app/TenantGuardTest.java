package com.festa.organization.app;

import com.festa.TestcontainersConfiguration;
import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.shared.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TenantGuardTest {

	@Autowired
	TenantGuard guard;

	@Autowired
	JdbcClient jdbc;

	UUID organization;
	UUID promoter;
	UUID outsider;

	@BeforeEach
	void setUp() {
		organization = UUID.randomUUID();
		jdbc.sql("INSERT INTO organizations (id, name, slug) VALUES (?, 'Atlética', ?)")
			.params(organization, "org-" + organization).update();
		promoter = insertUser();
		outsider = insertUser();
		jdbc.sql("INSERT INTO organization_members (id, organization_id, user_id, role) VALUES (?, ?, ?, 'PROMOTER')")
			.params(UUID.randomUUID(), organization, promoter).update();
	}

	@Test
	void anyRoleIsEnoughWhenNoneIsRequired() {
		assertThat(guard.requireRole(organization, promoter)).isEqualTo(Role.PROMOTER);
	}

	@Test
	void allowsListedRole() {
		assertThat(guard.requireRole(organization, promoter, Role.MANAGER, Role.PROMOTER)).isEqualTo(Role.PROMOTER);
	}

	@Test
	void memberWithoutRequiredRoleIsForbidden() {
		assertThatThrownBy(() -> guard.requireRole(organization, promoter, Role.OWNER, Role.ADMIN))
			.isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
	}

	@Test
	void nonMemberGetsNotFoundEvenForExistingOrganization() {
		assertThatThrownBy(() -> guard.requireRole(organization, outsider))
			.isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	private UUID insertUser() {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO users (id, name, email) VALUES (?, 'Usuário', ?)").params(id, id + "@festa.test").update();
		return id;
	}

}
