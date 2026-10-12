package com.festa.payment.infra;

import com.festa.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	/** Travado: dois webhooks do mesmo pagamento não conciliam ao mesmo tempo. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Payment p WHERE p.provider = :provider AND p.providerPaymentId = :providerPaymentId")
	Optional<Payment> lockByProviderId(Payment.Provider provider, String providerPaymentId);

	Optional<Payment> findByIdempotencyKey(String idempotencyKey);

	List<Payment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

	long countByOrderIdAndMethod(UUID orderId, Payment.Method method);

}
