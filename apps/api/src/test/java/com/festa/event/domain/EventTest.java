package com.festa.event.domain;

import com.festa.event.api.EventStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

	private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
	private static final Instant STARTS = Instant.parse("2026-11-14T02:00:00Z");
	private static final Instant ENDS = STARTS.plus(6, ChronoUnit.HOURS);

	@Test
	void startsAsDraftWithDefaults() {
		Event event = draft();

		assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
		assertThat(event.getMinAge()).isEqualTo(18);
		assertThat(event.getHalfPriceQuotaPercent()).isEqualTo(40);
	}

	@Test
	void publishesWhenComplete() {
		Event event = ready();

		event.publish(NOW, true);

		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.getPublishedAt()).isEqualTo(NOW);
	}

	@Test
	void listsWhatIsMissingBeforePublishing() {
		Event event = draft();

		assertThatThrownBy(() -> event.publish(NOW, false))
			.isInstanceOfSatisfying(EventRuleException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("event-not-ready");
				assertThat(ex.isConflict()).isFalse();
				assertThat(ex.getMissing()).containsExactly("startsAt", "endsAt", "venueName", "flyer");
			});
		assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
	}

	@Test
	void doesNotPublishEventThatAlreadyStarted() {
		Event event = ready();

		assertThatThrownBy(() -> event.publish(STARTS.plusSeconds(1), true))
			.isInstanceOfSatisfying(EventRuleException.class,
				ex -> assertThat(ex.getCode()).isEqualTo("starts-in-the-past"));
	}

	@Test
	void transitionsFollowTheStateMachine() {
		Event published = ready();
		published.publish(NOW, true);
		published.end();
		assertThat(published.getStatus()).isEqualTo(EventStatus.ENDED);
		assertConflict(published::cancel);
		assertConflict(() -> published.publish(NOW, true));

		Event draft = draft();
		assertConflict(draft::end);
		draft.cancel();
		assertThat(draft.getStatus()).isEqualTo(EventStatus.CANCELLED);
		assertConflict(() -> draft.update(change(null)));
	}

	@Test
	void cancelsPublishedEvent() {
		Event event = ready();
		event.publish(NOW, true);

		event.cancel();

		assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
	}

	@Test
	void rejectsEndBeforeStart() {
		Event event = draft();

		assertThatThrownBy(() -> event.update(new Event.Changes(null, null, null, STARTS, STARTS.minusSeconds(60),
				null, null, null, null, null, null, null, null)))
			.isInstanceOfSatisfying(EventRuleException.class, ex -> assertThat(ex.getCode()).isEqualTo("invalid-dates"));
	}

	@Test
	void openBarRequires18Plus() {
		Event event = draft();
		event.update(new Event.Changes(null, null, null, null, null, null, null, null, 16, null, null, null, null));

		assertThatThrownBy(() -> event.update(
				new Event.Changes(null, null, null, null, null, null, null, null, null, true, null, null, null)))
			.isInstanceOfSatisfying(EventRuleException.class,
				ex -> assertThat(ex.getCode()).isEqualTo("open-bar-needs-18"));
	}

	@Test
	void blankTextClearsOptionalFieldButPublishedEventKeepsVenue() {
		Event event = ready();
		event.update(new Event.Changes(null, "Open bar até 3h", null, null, null, null, null, null, null, null, null,
				null, null));
		event.update(new Event.Changes(null, "  ", null, null, null, null, null, null, null, null, null, null, null));
		assertThat(event.getDescription()).isNull();

		event.publish(NOW, true);
		assertThatThrownBy(() -> event.update(change("")))
			.isInstanceOfSatisfying(EventRuleException.class, ex -> assertThat(ex.getCode()).isEqualTo("venue-required"));
	}

	private static void assertConflict(Runnable action) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(EventRuleException.class, ex -> assertThat(ex.isConflict()).isTrue());
	}

	private static Event.Changes change(String venueName) {
		return new Event.Changes(null, null, null, null, null, venueName, null, null, null, null, null, null, null);
	}

	private static Event draft() {
		return new Event(UUID.randomUUID(), "Calourada Med 26", "calourada-med-26");
	}

	private static Event ready() {
		Event event = draft();
		event.update(new Event.Changes(null, null, null, STARTS, ENDS, "Galpão 42", null, null, null, null, null, null,
				null));
		return event;
	}

}
