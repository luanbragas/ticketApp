package com.festa.payment.infra;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** "state" do OAuth do gateway: guardado só como hash, uso único e preso à organização e a quem iniciou. */
@Repository
public class OAuthStateStore {

	private final JdbcClient jdbc;

	OAuthStateStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void save(String stateHash, UUID organizationId, UUID userId, Instant expiresAt) {
		jdbc.sql("""
				INSERT INTO payment_oauth_states (state_hash, organization_id, user_id, expires_at)
				VALUES (:hash, :organizationId, :userId, :expiresAt)
				""")
			.param("hash", stateHash)
			.param("organizationId", organizationId)
			.param("userId", userId)
			.param("expiresAt", Timestamp.from(expiresAt))
			.update();
	}

	/** Consome o state numa tacada só: falso se não existe, venceu, já foi usado ou é de outra pessoa. */
	public boolean consume(String stateHash, UUID organizationId, UUID userId, Instant now) {
		return jdbc.sql("""
				UPDATE payment_oauth_states SET used_at = :now
				 WHERE state_hash = :hash AND organization_id = :organizationId AND user_id = :userId
				   AND used_at IS NULL AND expires_at > :now
				""")
			.param("now", Timestamp.from(now))
			.param("hash", stateHash)
			.param("organizationId", organizationId)
			.param("userId", userId)
			.update() == 1;
	}

}
