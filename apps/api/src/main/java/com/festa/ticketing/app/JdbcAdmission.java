package com.festa.ticketing.app;

import com.festa.shared.crypto.PersonalDataCipher;
import com.festa.shared.text.Cpf;
import com.festa.ticketing.api.Admission;
import com.festa.ticketing.domain.TicketTokens;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Entrada por SQL atômico sobre {@code tickets} (ADR-010). */
@Service
class JdbcAdmission implements Admission {

	private static final String PASS_COLUMNS = """
			t.id, t.holder_name, ty.name AS type_name, b.name AS batch_name, t.is_half_price, t.status
			  FROM tickets t
			  JOIN ticket_batches b ON b.id = t.ticket_batch_id
			  JOIN ticket_types ty ON ty.id = b.ticket_type_id
			""";

	private final JdbcClient jdbc;
	private final PersonalDataCipher cipher;

	JdbcAdmission(JdbcClient jdbc, PersonalDataCipher cipher) {
		this.jdbc = jdbc;
		this.cipher = cipher;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Result admitByToken(UUID eventId, String token) {
		if (!TicketTokens.looksValid(token)) {
			return new Result(Outcome.NOT_VALID, null);
		}
		return admitByTokenHash(eventId, TicketTokens.hash(token));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Result admitByTokenHash(UUID eventId, String tokenHash) {
		Optional<UUID> ticketId = jdbc.sql("SELECT id FROM tickets WHERE event_id = :eventId AND token_hash = :hash")
			.param("eventId", eventId)
			.param("hash", tokenHash)
			.query(UUID.class)
			.optional();
		return ticketId.map(id -> admitById(eventId, id)).orElse(new Result(Outcome.NOT_VALID, null));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Result admitById(UUID eventId, UUID ticketId) {
		int admitted = jdbc.sql("""
				UPDATE tickets SET status = 'CHECKED_IN', updated_at = now()
				 WHERE id = :id AND event_id = :eventId AND status = 'VALID'
				""")
			.param("id", ticketId)
			.param("eventId", eventId)
			.update();
		Optional<Pass> pass = pass(eventId, ticketId);
		if (pass.isEmpty()) {
			return new Result(Outcome.NOT_VALID, null);
		}
		if (admitted == 1) {
			return new Result(Outcome.ADMITTED, pass.get());
		}
		return new Result("CHECKED_IN".equals(pass.get().status()) ? Outcome.ALREADY_IN : Outcome.NOT_VALID,
				pass.get());
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean readmit(UUID eventId, UUID ticketId) {
		return jdbc.sql("""
				UPDATE tickets SET status = 'VALID', updated_at = now()
				 WHERE id = :id AND event_id = :eventId AND status = 'CHECKED_IN'
				""")
			.param("id", ticketId)
			.param("eventId", eventId)
			.update() == 1;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ManifestEntry> manifest(UUID eventId) {
		return jdbc.sql("SELECT t.token_hash, " + PASS_COLUMNS + " WHERE t.event_id = :eventId ORDER BY t.holder_name")
			.param("eventId", eventId)
			.query((rs, i) -> {
				Pass pass = pass(rs);
				return new ManifestEntry(pass.ticketId(), rs.getString("token_hash"), pass.holderName(), pass.typeName(),
						pass.halfPrice(), pass.status());
			})
			.list();
	}

	@Override
	@Transactional(readOnly = true)
	public List<Attendee> attendees(UUID eventId, String query, String status, int limit) {
		String q = query == null ? "" : query.trim();
		String cpfHash = Cpf.isValid(q) ? cipher.hash(Cpf.digits(q)) : null;
		return jdbc.sql("SELECT t.holder_cpf_encrypted, t.buyer_email, " + PASS_COLUMNS + """
				 WHERE t.event_id = :eventId
				   AND (CAST(:status AS text) IS NULL OR t.status = CAST(:status AS text))
				   AND (:q = '' OR t.holder_cpf_hash = CAST(:cpfHash AS text)
				        OR (CAST(:cpfHash AS text) IS NULL AND t.holder_name ILIKE '%' || :like || '%'))
				 ORDER BY t.holder_name
				 LIMIT :limit
				""")
			.param("eventId", eventId)
			.param("status", status)
			.param("q", q)
			.param("cpfHash", cpfHash)
			.param("like", q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"))
			.param("limit", limit)
			.query((rs, i) -> new Attendee(pass(rs), Cpf.mask(cipher.decrypt(rs.getBytes("holder_cpf_encrypted"))),
					rs.getString("buyer_email")))
			.list();
	}

	@Override
	@Transactional(readOnly = true)
	public Counts counts(UUID eventId) {
		return jdbc.sql("""
				SELECT count(*) FILTER (WHERE status IN ('VALID', 'CHECKED_IN')) AS issued,
				       count(*) FILTER (WHERE status = 'CHECKED_IN') AS checked_in
				  FROM tickets WHERE event_id = :eventId
				""")
			.param("eventId", eventId)
			.query((rs, i) -> new Counts(rs.getInt("issued"), rs.getInt("checked_in")))
			.single();
	}

	private Optional<Pass> pass(UUID eventId, UUID ticketId) {
		return jdbc.sql("SELECT " + PASS_COLUMNS + " WHERE t.id = :id AND t.event_id = :eventId")
			.param("id", ticketId)
			.param("eventId", eventId)
			.query((rs, i) -> pass(rs))
			.optional();
	}

	private static Pass pass(ResultSet rs) throws SQLException {
		return new Pass(rs.getObject("id", UUID.class), rs.getString("holder_name"), rs.getString("type_name"),
				rs.getString("batch_name"), rs.getBoolean("is_half_price"), rs.getString("status"));
	}

}
