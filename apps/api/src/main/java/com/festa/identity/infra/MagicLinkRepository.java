package com.festa.identity.infra;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** SQL nativo (ADR-002): o consumo precisa ser atômico para o link valer uma única vez. */
@Repository
public class MagicLinkRepository {

	private final JdbcClient jdbc;

	MagicLinkRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(UUID id, String email, String tokenHash, Instant expiresAt, Instant now) {
		jdbc.sql("""
				INSERT INTO magic_links (id, email, token_hash, expires_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""")
			.params(id, email, tokenHash, ts(expiresAt), ts(now), ts(now))
			.update();
	}

	public int countCreatedSince(String email, Instant since) {
		return jdbc.sql("SELECT count(*) FROM magic_links WHERE email = ? AND created_at > ?")
			.params(email, ts(since))
			.query(Integer.class)
			.single();
	}

	/** Marca o link como usado se ainda for válido e devolve o e-mail; vazio se inválido, expirado ou já usado. */
	public Optional<String> consume(String tokenHash, Instant now) {
		return jdbc.sql("""
				UPDATE magic_links
				   SET used_at = ?, updated_at = ?
				 WHERE token_hash = ?
				   AND used_at IS NULL
				   AND expires_at > ?
				RETURNING email
				""")
			.params(ts(now), ts(now), tokenHash, ts(now))
			.query(String.class)
			.optional();
	}

	private static Timestamp ts(Instant instant) {
		return Timestamp.from(instant);
	}

}
