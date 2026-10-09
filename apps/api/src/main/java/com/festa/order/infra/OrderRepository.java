package com.festa.order.infra;

import com.festa.order.domain.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

	Optional<Order> findByAccessKeyHash(String accessKeyHash);

	/** Pedido travado: pagamento e expiração do mesmo pedido não decidem ao mesmo tempo. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM Order o WHERE o.id = :id")
	Optional<Order> findByIdForUpdate(UUID id);

}
