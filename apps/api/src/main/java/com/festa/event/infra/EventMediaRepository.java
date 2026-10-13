package com.festa.event.infra;

import com.festa.event.domain.EventMedia;
import com.festa.event.domain.EventMedia.Kind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventMediaRepository extends JpaRepository<EventMedia, UUID> {

	Optional<EventMedia> findByEventIdAndKind(UUID eventId, Kind kind);

	List<EventMedia> findByEventIdInAndKind(Collection<UUID> eventIds, Kind kind);

}
