package com.festa.event.infra;

import com.festa.event.domain.LineupItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LineupRepository extends JpaRepository<LineupItem, UUID> {

	List<LineupItem> findByEventIdOrderByPosition(UUID eventId);

	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM LineupItem l WHERE l.eventId = :eventId")
	void deleteByEventId(UUID eventId);

}
