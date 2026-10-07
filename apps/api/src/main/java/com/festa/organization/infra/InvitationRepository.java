package com.festa.organization.infra;

import com.festa.organization.domain.Invitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

	Optional<Invitation> findByTokenHash(String tokenHash);

	/** Trava a linha (SELECT ... FOR UPDATE) para o convite ser aceito uma única vez. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Invitation i where i.tokenHash = :tokenHash")
	Optional<Invitation> findByTokenHashForUpdate(String tokenHash);

	@Query("""
			select i from Invitation i
			 where i.organizationId = :organizationId and i.acceptedAt is null and i.expiresAt > :now
			 order by i.createdAt
			""")
	List<Invitation> findPending(UUID organizationId, Instant now);

}
