package com.festa.payment.infra;

import com.festa.payment.domain.PaymentAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, UUID> {

	Optional<PaymentAccount> findByOrganizationId(UUID organizationId);

	boolean existsByOrganizationId(UUID organizationId);

}
