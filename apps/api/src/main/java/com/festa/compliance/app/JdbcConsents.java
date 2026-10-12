package com.festa.compliance.app;

import com.festa.compliance.api.Consents;
import com.festa.shared.id.UuidV7;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
class JdbcConsents implements Consents {

	private final JdbcClient jdbc;
	private final Clock clock;

	JdbcConsents(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	private record Row(UUID id, Version version) {
	}

	@Override
	@Transactional(readOnly = true)
	public List<Version> current() {
		return rows().stream().map(Row::version).toList();
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public String accept(UUID userId, String email, Purpose purpose, UUID referenceId, Origin origin) {
		List<Row> current = rows();
		if (current.stream().map(r -> r.version().kind()).distinct().count() < 2) {
			throw new IllegalStateException("termos ou política de privacidade sem versão publicada");
		}
		for (Row row : current) {
			jdbc.sql("""
					INSERT INTO consent_records (id, user_id, email, terms_version_id, purpose, reference_id, ip, user_agent)
					VALUES (:id, :userId, :email, :versionId, :purpose, :referenceId, :ip, :userAgent)
					""")
				.param("id", UuidV7.generate())
				.param("userId", userId)
				.param("email", email.trim().toLowerCase(Locale.ROOT))
				.param("versionId", row.id())
				.param("purpose", purpose.name())
				.param("referenceId", referenceId)
				.param("ip", origin == null ? null : origin.ip())
				.param("userAgent", origin == null ? null : truncate(origin.userAgent()))
				.update();
		}
		return current.stream()
			.filter(r -> "TERMS".equals(r.version().kind()))
			.findFirst()
			.orElseThrow()
			.version()
			.version();
	}

	/** A mais recente já publicada de cada tipo. */
	private List<Row> rows() {
		return jdbc.sql("""
				SELECT DISTINCT ON (kind) id, kind, version, url, published_at
				  FROM terms_versions
				 WHERE published_at <= :now
				 ORDER BY kind, published_at DESC
				""")
			.param("now", Timestamp.from(clock.instant()))
			.query((rs, i) -> new Row(rs.getObject("id", UUID.class), new Version(rs.getString("kind"),
					rs.getString("version"), rs.getString("url"), rs.getTimestamp("published_at").toInstant())))
			.list();
	}

	private static String truncate(String userAgent) {
		return userAgent == null || userAgent.length() <= 500 ? userAgent : userAgent.substring(0, 500);
	}

}
