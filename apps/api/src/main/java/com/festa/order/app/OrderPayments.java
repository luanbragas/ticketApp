package com.festa.order.app;

import com.festa.order.api.OrderPaid;
import com.festa.order.domain.Order;
import com.festa.order.domain.OrderItem;
import com.festa.order.domain.OrderStatus;
import com.festa.order.infra.OrderItemRepository;
import com.festa.order.infra.OrderRepository;
import com.festa.shared.outbox.OutboxPublisher;
import com.festa.ticketing.api.Inventory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Confirmação de pagamento do pedido (ARCHITECTURE.md §Pagamento, passo 4d). Não tem rota HTTP: quem chama
 * é o webhook do gateway depois de validar a assinatura e consultar o pagamento (M5, CLAUDE.md regra 4).
 */
@Service
public class OrderPayments {

	private final OrderRepository orders;
	private final OrderItemRepository items;
	private final Inventory inventory;
	private final OutboxPublisher outbox;
	private final Clock clock;

	OrderPayments(OrderRepository orders, OrderItemRepository items, Inventory inventory, OutboxPublisher outbox,
			Clock clock) {
		this.orders = orders;
		this.items = items;
		this.inventory = inventory;
		this.outbox = outbox;
		this.clock = clock;
	}

	/**
	 * Pedido aguardando pagamento → pago: a reserva vira venda e {@link OrderPaid} vai para o outbox, tudo
	 * numa transação. Repetir para um pedido já pago não faz nada (o webhook pode chegar duas vezes).
	 *
	 * @return {@code true} se o pedido mudou para pago agora
	 */
	@Transactional
	public boolean confirmPaid(UUID orderId) {
		Order order = orders.findByIdForUpdate(orderId).orElseThrow();
		if (order.getStatus() == OrderStatus.PAID) {
			return false;
		}
		// Pedido expirado com pagamento aprovado é o caso de borda do M5 (reservar de novo ou estornar).
		Instant now = clock.instant();
		order.markPaid(now);
		List<OrderItem> orderItems = items.findByOrderIdOrderByPosition(orderId);
		Map<UUID, Long> perBatch = orderItems.stream()
			.collect(Collectors.groupingBy(OrderItem::getTicketBatchId, Collectors.counting()));
		perBatch.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> inventory.confirm(order.getEventId(), entry.getKey(), entry.getValue().intValue()));
		outbox.publish(OrderPaid.TYPE, orderId, new OrderPaid(orderId, order.getEventId(), order.getOrganizationId(),
				order.getBuyerName(), order.getBuyerEmail(),
				orderItems.stream()
					.map(item -> new OrderPaid.Item(item.getId(), item.getTicketBatchId(), item.getHolderName(),
							Base64.getEncoder().encodeToString(item.getHolderCpfEncrypted()), item.getHolderCpfHash(),
							item.isHalfPrice()))
					.toList()));
		return true;
	}

}
