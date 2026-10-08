package com.festa.order.app;

import com.festa.order.domain.Order;
import com.festa.order.domain.OrderItem;
import com.festa.order.infra.OrderItemRepository;
import com.festa.order.infra.OrderQueries;
import com.festa.order.infra.OrderRepository;
import com.festa.ticketing.api.Inventory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Pedido que venceu sem pagamento expira e devolve a reserva ao lote (ARCHITECTURE.md §Estoque). */
@Service
public class OrderExpiryService {

	/** Por rodada: o job chama de novo enquanto houver vencidos. */
	static final int BATCH_SIZE = 200;

	private final OrderRepository orders;
	private final OrderItemRepository items;
	private final OrderQueries queries;
	private final Inventory inventory;
	private final Clock clock;

	OrderExpiryService(OrderRepository orders, OrderItemRepository items, OrderQueries queries, Inventory inventory,
			Clock clock) {
		this.orders = orders;
		this.items = items;
		this.queries = queries;
		this.inventory = inventory;
		this.clock = clock;
	}

	/**
	 * Expira até {@link #BATCH_SIZE} pedidos vencidos numa transação: status e estoque mudam juntos.
	 *
	 * @return quantos pedidos expiraram
	 */
	@Transactional
	public int expireDue() {
		Instant now = clock.instant();
		List<UUID> ids = queries.lockExpired(now, BATCH_SIZE);
		if (ids.isEmpty()) {
			return 0;
		}
		List<Order> due = orders.findAllById(ids);
		due.forEach(order -> order.expire(now));
		Map<UUID, Long> reserved = items.findByOrderIdIn(ids).stream()
			.collect(Collectors.groupingBy(OrderItem::getTicketBatchId, Collectors.counting()));
		// Mesma ordem de lote da reserva: sem espera circular com quem está comprando.
		reserved.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> inventory.release(entry.getKey(), entry.getValue().intValue()));
		return due.size();
	}

}
