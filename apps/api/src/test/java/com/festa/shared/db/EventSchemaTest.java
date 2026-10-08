package com.festa.shared.db;

import com.festa.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Restrições da migration V005 (events, event_media, event_lineup). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EventSchemaTest {

	private static final Instant STARTS = Instant.parse("2026-11-14T02:00:00Z");

	@Autowired
	JdbcClient jdbc;

	UUID org;

	@BeforeEach
	void setUp() {
		org = insertOrganization("atletica-medicina");
	}

	@Test
	void draftNeedsOnlyNameAndSlugAndDefaultsTo18Plus() {
		UUID event = insertDraft("calourada-med-26");

		var row = jdbc.sql("SELECT status, min_age, half_price_quota_percent FROM events WHERE id = ?")
			.param(event).query().singleRow();
		assertThat(row).containsEntry("status", "DRAFT")
			.containsEntry("min_age", 18)
			.containsEntry("half_price_quota_percent", 40);
	}

	@Test
	void rejectsPublishingWithoutDateAndVenue() {
		UUID event = insertDraft("calourada-med-26");

		assertThatThrownBy(() -> jdbc.sql("UPDATE events SET status = 'PUBLISHED', published_at = now() WHERE id = ?")
			.param(event).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void publishesWithDateVenueAndPublishedAt() {
		UUID event = insertDraft("calourada-med-26");

		jdbc.sql("""
				UPDATE events SET status = 'PUBLISHED', published_at = now(),
				  starts_at = ?, ends_at = ?, venue_name = 'Galpão 42' WHERE id = ?""")
			.params(ts(STARTS), ts(STARTS.plus(6, ChronoUnit.HOURS)), event).update();
		assertThat(status(event)).isEqualTo("PUBLISHED");
	}

	@Test
	void rejectsEndBeforeStart() {
		UUID event = insertDraft("calourada-med-26");

		assertThatThrownBy(() -> jdbc.sql("UPDATE events SET starts_at = ?, ends_at = ? WHERE id = ?")
			.params(ts(STARTS), ts(STARTS.minus(1, ChronoUnit.HOURS)), event).update())
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void openBarRequires18Plus() {
		UUID event = insertDraft("calourada-med-26");

		assertThatThrownBy(() -> jdbc.sql("UPDATE events SET has_open_bar = true, min_age = 16 WHERE id = ?")
			.param(event).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsDuplicateSlugAcrossOrganizations() {
		insertDraft("calourada-med-26");
		UUID other = insertOrganization("atletica-direito");

		assertThatThrownBy(() -> insertDraft(other, "calourada-med-26")).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void rejectsAccentColorOutsideLowercaseHex() {
		UUID event = insertDraft("calourada-med-26");

		jdbc.sql("UPDATE events SET accent_color = '#e8262c' WHERE id = ?").param(event).update();

		assertThatThrownBy(() -> jdbc.sql("UPDATE events SET accent_color = 'red' WHERE id = ?").param(event).update())
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void allowsOnlyOneFlyerPerEvent() {
		UUID event = insertDraft("calourada-med-26");
		insertMedia(event, org, "FLYER", 0);
		insertMedia(event, org, "GALLERY", 0);

		assertThatThrownBy(() -> insertMedia(event, org, "FLYER", 1)).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void mediaCannotPointToEventOfAnotherOrganization() {
		UUID event = insertDraft("calourada-med-26");
		UUID other = insertOrganization("atletica-direito");

		assertThatThrownBy(() -> insertMedia(event, other, "FLYER", 0))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void lineupCannotPointToEventOfAnotherOrganization() {
		UUID event = insertDraft("calourada-med-26");
		UUID other = insertOrganization("atletica-direito");

		assertThatThrownBy(() -> insertLineup(event, other, "DJ Kaio", 0))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void lineupPositionsAreUniquePerEvent() {
		UUID event = insertDraft("calourada-med-26");
		insertLineup(event, org, "DJ Kaio Ramos", 0);

		assertThatThrownBy(() -> insertLineup(event, org, "MC Lia", 0)).isInstanceOf(DuplicateKeyException.class);
	}

	private UUID insertOrganization(String slug) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO organizations (id, name, slug) VALUES (?, ?, ?)").params(id, "Atlética", slug).update();
		return id;
	}

	private UUID insertDraft(String slug) {
		return insertDraft(org, slug);
	}

	private UUID insertDraft(UUID organizationId, String slug) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO events (id, organization_id, slug, name) VALUES (?, ?, ?, ?)")
			.params(id, organizationId, slug, "Calourada Med 26").update();
		return id;
	}

	private void insertMedia(UUID event, UUID organizationId, String kind, int position) {
		jdbc.sql("""
				INSERT INTO event_media (id, event_id, organization_id, kind, url, width, height, position)
				VALUES (?, ?, ?, ?, 'https://cdn.festa.app/flyer.jpg', 918, 1600, ?)""")
			.params(UUID.randomUUID(), event, organizationId, kind, position).update();
	}

	private void insertLineup(UUID event, UUID organizationId, String name, int position) {
		jdbc.sql("INSERT INTO event_lineup (id, event_id, organization_id, name, position) VALUES (?, ?, ?, ?, ?)")
			.params(UUID.randomUUID(), event, organizationId, name, position).update();
	}

	private String status(UUID event) {
		return jdbc.sql("SELECT status FROM events WHERE id = ?").param(event).query(String.class).single();
	}

	private static Timestamp ts(Instant instant) {
		return Timestamp.from(instant);
	}

}
