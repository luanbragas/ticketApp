package com.festa.order.infra;

import com.festa.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

	List<OrderItem> findByOrderIdOrderByPosition(UUID orderId);

	List<OrderItem> findByOrderIdIn(Collection<UUID> orderIds);

}
