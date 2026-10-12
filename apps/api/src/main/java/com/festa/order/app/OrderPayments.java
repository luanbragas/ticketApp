package com.festa.order.app;

import com.festa.event.api.EventDirectory;
import com.festa.order.api.OrderBilling;
import com.festa.order.api.OrderPaid;
import com.festa.order.domain.Order;
import com.festa.order.domain.OrderItem;
import com.festa.order.domain.OrderStatus;
import com.festa.order.infra.OrderItemRepository;
import com.festa.order.infra.OrderRepository;
import com.festa.shared.outbox.OutboxPublisher;
import com.festa.shared.security.SecureToken;
import com.festa.ticketing.api.Inventory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pagamento do pedido (ARCHITECTURE.md §Pagamento, passo 4d). Não tem rota HTTP: quem chama é o webhook do
 * gateway, depois de validar a assinatura e consultar o pagamento (CLAUDE.md regra 4).
 */
@Service
public class OrderPayments implements OrderBilling {

	private final OrderRepository orders;
	private final OrderItemRepository items;
	private final Inventory inventory;
	private final OutboxPublisher outbox;
	private final EventDirectory events;
	private final Clock clock;

	OrderPayments(OrderRepository orders, OrderItemRepository items, Inventory inventory, OutboxPublisher outbox,
			EventDirectory events, Clock clock) {
		this.orders = orders;
		this.items = items;
		this.inventory = inventory;
		this.outbox = outbox;
		this.events = events;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<BillableOrder> forBuyer(UUID orderId, String accessKey) {
		if (accessKey == null) {
			return Optional.empty();
		}
		return orders.findById(orderId)
			.filter(o -> SecureToken.constantTimeEquals(o.getAccessKeyHash(), SecureToken.hash(accessKey)))
			.map(this::billable);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<BillableOrder> get(UUID orderId) {
		return orders.findById(orderId).map(this::billable);
	}

	/**
	 * Aguardando pagamento → pago: a reserva vira venda e {@link OrderPaid} vai para o outbox, tudo numa
	 * transação. Se já tinha expirado, tenta reservar de novo; sem estoque, avisa para estornar.
	 */
	@Override
	@Transactional
	public PaidOutcome paid(UUID orderId) {
		Order order = orders.findByIdForUpdate(orderId).orElseThrow();
		Instant now = clock.instant();
		List<OrderItem> orderItems = items.findByOrderIdOrderByPosition(orderId);
		switch (order.getStatus()) {
			case PAID -> {
				return PaidOutcome.ALREADY_PAID;
			}
			case PENDING_PAYMENT -> {
				order.markPaid(now);
				settle(order, orderItems, now);
				return PaidOutcome.CONFIRMED;
			}
			case EXPIRED -> {
				if (!reserveAgain(order, orderItems, now)) {
					return PaidOutcome.NO_STOCK;
				}
				order.markPaidAfterExpiry(now);
				settle(order, orderItems, now);
				return PaidOutcome.REVIVED;
			}
			default -> {
				// Já estornado ou com falha: um segundo pagamento aprovado também volta para o comprador.
				return PaidOutcome.NO_STOCK;
			}
		}
	}

	@Override
	@Transactional
	public void refunded(UUID orderId) {
		Order order = orders.findByIdForUpdate(orderId).orElseThrow();
		if (order.getStatus() == OrderStatus.EXPIRED) {
			order.markRefunded();
		}
	}

	/** Atalho dos testes e do webhook: {@code true} se o pedido mudou para pago agora. */
	@Transactional
	public boolean confirmPaid(UUID orderId) {
		PaidOutcome outcome = paid(orderId);
		return outcome == PaidOutcome.CONFIRMED || outcome == PaidOutcome.REVIVED;
	}

	private BillableOrder billable(Order o) {
		String eventName = events.find(o.getOrganizationId(), o.getEventId()).map(e -> e.name()).orElse("Evento");
		return new BillableOrder(o.getId(), o.getEventId(), o.getOrganizationId(), eventName, o.getStatus().name(),
				o.getTotalCents(), o.getFeeCents(), o.getBuyerName(), o.getBuyerEmail(), o.getExpiresAt());
	}

	/** Reserva de novo o que o pedido tinha; se algum lote não tiver mais, devolve o que pegou e falha. */
	private boolean reserveAgain(Order order, List<OrderItem> orderItems, Instant now) {
		List<Map.Entry<UUID, Integer>> taken = new ArrayList<>();
		for (Map.Entry<UUID, Integer> entry : perBatch(orderItems).entrySet()) {
			if (inventory.reserve(order.getEventId(), entry.getKey(), entry.getValue(), now).isEmpty()) {
				taken.forEach(t -> inventory.release(t.getKey(), t.getValue()));
				return false;
			}
			taken.add(entry);
		}
		return true;
	}

	/** Reserva vira venda (com virada de lote) e o evento de domínio vai para o outbox. */
	private void settle(Order order, List<OrderItem> orderItems, Instant now) {
		perBatch(orderItems).forEach((batchId, quantity) -> inventory.confirm(order.getEventId(), batchId, quantity));
		outbox.publish(OrderPaid.TYPE, order.getId(), new OrderPaid(order.getId(), order.getEventId(),
				order.getOrganizationId(), order.getBuyerName(), order.getBuyerEmail(), order.getPromoterId(),
				order.getSubtotalCents(), now,
				orderItems.stream()
					.map(item -> new OrderPaid.Item(item.getId(), item.getTicketBatchId(), item.getHolderName(),
							Base64.getEncoder().encodeToString(item.getHolderCpfEncrypted()), item.getHolderCpfHash(),
							item.isHalfPrice()))
					.toList()));
	}

	/** Quantidade por lote, em ordem de id (mesma ordem da reserva: sem espera circular). */
	private static Map<UUID, Integer> perBatch(List<OrderItem> orderItems) {
		return orderItems.stream()
			.collect(Collectors.groupingBy(OrderItem::getTicketBatchId, TreeMap::new, Collectors.summingInt(i -> 1)));
	}

}
