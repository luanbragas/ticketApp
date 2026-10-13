package com.festa.event.app;

import com.festa.event.api.EventDirectory;
import com.festa.event.api.EventStatus;
import com.festa.event.domain.Event;
import com.festa.event.infra.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
class JpaEventDirectory implements EventDirectory {

	private final EventRepository events;

	JpaEventDirectory(EventRepository events) {
		this.events = events;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<EventRef> find(UUID organizationId, UUID eventId) {
		return events.findByIdAndOrganizationId(eventId, organizationId).map(JpaEventDirectory::ref);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<EventRef> findPublic(String slug) {
		return events.findBySlug(slug)
			.filter(e -> e.getStatus() == EventStatus.PUBLISHED || e.getStatus() == EventStatus.ENDED)
			.map(JpaEventDirectory::ref);
	}

	private static EventRef ref(Event e) {
		return new EventRef(e.getId(), e.getOrganizationId(), e.getSlug(), e.getName(), e.getStatus(), e.getMinAge(),
			e.getHalfPriceQuotaPercent(), e.getMaxTicketsPerCpf(), e.getStartsAt(), e.getEndsAt());
	}

}
