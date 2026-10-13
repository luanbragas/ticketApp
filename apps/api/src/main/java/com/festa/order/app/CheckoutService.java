package com.festa.order.app;

import com.festa.event.api.EventDirectory;
import com.festa.event.api.EventDirectory.EventRef;
import com.festa.event.api.EventStatus;
import com.festa.order.domain.FeePolicy;
import com.festa.order.domain.HalfPriceReason;
import com.festa.order.domain.Order;
import com.festa.order.domain.OrderItem;
import com.festa.order.domain.OrderRuleException;
import com.festa.order.infra.OrderItemRepository;
import com.festa.order.infra.OrderQueries;
import com.festa.order.infra.OrderRepository;
import com.festa.promoter.api.PromoterLinks;
import com.festa.shared.crypto.PersonalDataCipher;
import com.festa.shared.security.SecureToken;
import com.festa.shared.text.Cpf;
import com.festa.shared.web.ApiException;
import com.festa.ticketing.api.Inventory;
import com.festa.ticketing.api.Inventory.Offer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Seleção e criação de pedido na página pública (ARCHITECTURE.md §Estoque, ADR-007). Preço, taxa e total
 * saem sempre daqui; o navegador só mostra.
 */
@Service
public class CheckoutService {

	/** Teto de ingressos num pedido, somando todos os lotes. */
	public static final int MAX_TICKETS_PER_ORDER = 20;

	private final EventDirectory events;
	private final PromoterLinks promoters;
	private final Inventory inventory;
	private final OrderRepository orders;
	private final OrderItemRepository items;
	private final OrderQueries queries;
	private final PersonalDataCipher cipher;
	private final FeePolicy fees;
	private final TransactionTemplate tx;
	private final Clock clock;
	private final Duration reservationTtl;
	private final String termsVersion;

	CheckoutService(EventDirectory events, PromoterLinks promoters, Inventory inventory, OrderRepository orders, OrderItemRepository items,
			OrderQueries queries, PersonalDataCipher cipher, FeePolicy fees, PlatformTransactionManager transactions,
			Clock clock, @Value("${festa.orders.reservation-ttl}") Duration reservationTtl,
			@Value("${festa.legal.terms-version}") String termsVersion) {
		this.events = events;
		this.promoters = promoters;
		this.inventory = inventory;
		this.orders = orders;
		this.items = items;
		this.queries = queries;
		this.cipher = cipher;
		this.fees = fees;
		this.tx = new TransactionTemplate(transactions);
		this.clock = clock;
		this.reservationTtl = reservationTtl;
		this.termsVersion = termsVersion;
	}

	/** Linha do resumo: um lote com a quantidade escolhida. */
	public record Line(Offer offer, int quantity, long unitPriceCents, long unitFeeCents) {

		public long totalCents() {
			return (unitPriceCents + unitFeeCents) * quantity;
		}

	}

	public record Quote(EventRef event, List<Line> lines, long subtotalCents, long feeCents, long totalCents) {
	}

	public record Buyer(String name, String email, String phone, String cpf) {
	}

	public record TicketRequest(UUID batchId, String holderName, String holderCpf, HalfPriceReason halfPriceReason) {
	}

	/**
	 * @param accessKey o Idempotency-Key gerado pelo navegador: repetir a mesma chave devolve o mesmo
	 *        pedido, e só quem tem a chave acompanha o status
	 */
	/** @param promoterCode código do link de promoter guardado pelo navegador; desconhecido é ignorado */
	public record PlaceOrder(String eventSlug, String accessKey, Buyer buyer, List<TicketRequest> tickets,
			boolean adultDeclared, boolean termsAccepted, String promoterCode) {
	}

	/** Pedido com os itens e o nome dos lotes, para a resposta. {@code created} = falso quando repetido. */
	public record Placed(EventRef event, Order order, List<OrderItem> items, Map<UUID, Offer> offers, boolean created) {
	}

	/** Resumo sem reservar: valida a seleção e calcula subtotal, taxa e total. */
	public Quote quote(String eventSlug, Map<UUID, Integer> selection) {
		EventRef event = eventOnSale(eventSlug);
		return quoteFor(event, selection);
	}

	/**
	 * Cria o pedido {@code PENDING_PAYMENT} e reserva o estoque na mesma transação: se qualquer lote não
	 * tiver estoque, nada fica reservado. A mesma chave de acesso devolve o pedido já criado.
	 */
	public Placed place(PlaceOrder command) {
		String keyHash = SecureToken.hash(command.accessKey());
		var existing = existing(keyHash, command.eventSlug());
		if (existing != null) {
			return existing;
		}
		try {
			return tx.execute(status -> create(command, keyHash));
		}
		catch (DataIntegrityViolationException ex) {
			// Dois envios com a mesma chave ao mesmo tempo: o segundo devolve o que o primeiro criou.
			var raced = existing(keyHash, command.eventSlug());
			if (raced != null) {
				return raced;
			}
			throw ex;
		}
	}

	/** Pedido pela chave de acesso. Chave errada responde igual a pedido inexistente. */
	public Placed find(UUID orderId, String accessKey) {
		Order order = orders.findById(orderId)
			.filter(o -> SecureToken.constantTimeEquals(o.getAccessKeyHash(), SecureToken.hash(accessKey)))
			.orElseThrow(CheckoutService::orderNotFound);
		return placed(order, false);
	}

	private Placed create(PlaceOrder command, String keyHash) {
		Instant now = clock.instant();
		EventRef event = eventOnSale(command.eventSlug());
		List<TicketRequest> tickets = command.tickets();
		if (tickets == null || tickets.isEmpty()) {
			throw rule("empty-order", "Escolha pelo menos um ingresso.");
		}
		if (!command.termsAccepted()) {
			throw rule("terms-required", "É preciso aceitar os termos de uso e a política de privacidade.");
		}
		if (event.minAge() >= 18 && !command.adultDeclared()) {
			throw rule("adult-declaration-required", "Essa festa é só para maiores de 18. Confirme a idade dos titulares.");
		}
		String buyerCpf = validCpf(command.buyer().cpf(), "buyer.cpf");

		Map<UUID, Integer> selection = tickets.stream()
			.collect(Collectors.groupingBy(TicketRequest::batchId, LinkedHashMap::new, Collectors.summingInt(t -> 1)));
		Quote quote = quoteFor(event, selection);
		Map<UUID, Offer> offers = quote.lines().stream()
			.collect(Collectors.toMap(l -> l.offer().batchId(), Line::offer));

		List<OrderItem.Holder> holders = new ArrayList<>();
		for (int i = 0; i < tickets.size(); i++) {
			TicketRequest ticket = tickets.get(i);
			Offer offer = offers.get(ticket.batchId());
			if (offer.halfPrice() && ticket.halfPriceReason() == null) {
				throw rule("half-price-reason-required", "Informe o benefício de meia-entrada do titular " + (i + 1) + ".",
						Map.of("field", "tickets[" + i + "].halfPriceReason"));
			}
			String holderCpf = validCpf(ticket.holderCpf(), "tickets[" + i + "].holderCpf");
			holders.add(new OrderItem.Holder(ticket.holderName(), cipher.encrypt(holderCpf), cipher.hash(holderCpf)));
		}

		String buyerCpfHash = cipher.hash(buyerCpf);
		if (event.maxTicketsPerCpf() != null) {
			queries.lockBuyer(event.id(), buyerCpfHash);
			int held = queries.ticketsHeldBy(event.id(), buyerCpfHash);
			int left = Math.max(0, event.maxTicketsPerCpf() - held);
			if (tickets.size() > left) {
				throw rule("cpf-limit", left == 0
						? "Esse CPF já chegou ao limite de " + event.maxTicketsPerCpf() + " ingressos neste evento."
						: "Esse CPF só pode comprar mais " + left + " ingresso(s) neste evento.",
						Map.of("limit", event.maxTicketsPerCpf(), "remaining", left));
			}
		}

		// Ordem fixa de lote evita espera circular entre dois pedidos que pegam os mesmos lotes.
		Map<UUID, Long> prices = new LinkedHashMap<>();
		for (UUID batchId : selection.keySet().stream().sorted(Comparator.naturalOrder()).toList()) {
			OptionalLong price = inventory.reserve(event.id(), batchId, selection.get(batchId), now);
			if (price.isEmpty()) {
				throw unavailable(offers.get(batchId));
			}
			prices.put(batchId, price.getAsLong());
		}

		long subtotal = 0;
		long fee = 0;
		for (TicketRequest ticket : tickets) {
			long price = prices.get(ticket.batchId());
			subtotal += price;
			fee += fees.feeFor(price);
		}
		Order order = orders.save(new Order(event.id(), event.organizationId(),
				new Order.Buyer(command.buyer().name(), command.buyer().email(), phone(command.buyer().phone()),
						cipher.encrypt(buyerCpf), buyerCpfHash),
				subtotal, fee, now, now.plus(reservationTtl), keyHash, command.adultDeclared(), termsVersion));
		promoters.resolve(event.id(), command.promoterCode()).ifPresent(order::attributeTo);
		List<OrderItem> saved = new ArrayList<>();
		for (int i = 0; i < tickets.size(); i++) {
			TicketRequest ticket = tickets.get(i);
			long price = prices.get(ticket.batchId());
			HalfPriceReason reason = offers.get(ticket.batchId()).halfPrice() ? ticket.halfPriceReason() : null;
			saved.add(new OrderItem(order.getId(), ticket.batchId(), price, fees.feeFor(price), holders.get(i), reason, i));
		}
		items.saveAll(saved);
		orders.flush();
		return new Placed(event, order, saved, offers, true);
	}

	private Quote quoteFor(EventRef event, Map<UUID, Integer> selection) {
		if (selection.isEmpty()) {
			throw rule("empty-order", "Escolha pelo menos um ingresso.");
		}
		int total = selection.values().stream().mapToInt(Integer::intValue).sum();
		if (total > MAX_TICKETS_PER_ORDER) {
			throw rule("too-many-tickets", "No máximo " + MAX_TICKETS_PER_ORDER + " ingressos por pedido.");
		}
		Map<UUID, Offer> offers = inventory.offers(event.id(), selection.keySet()).stream()
			.collect(Collectors.toMap(Offer::batchId, Function.identity()));
		List<Line> lines = new ArrayList<>();
		long subtotal = 0;
		long fee = 0;
		for (Map.Entry<UUID, Integer> entry : selection.entrySet()) {
			Offer offer = offers.get(entry.getKey());
			int quantity = entry.getValue();
			if (offer == null) {
				throw rule("unknown-batch", "Esse ingresso não é desta festa.", Map.of("batchId", entry.getKey()));
			}
			if (quantity < 1 || quantity > offer.maxPerOrder()) {
				throw rule("max-per-order", "No " + offer.batchName() + " de " + offer.typeName() + ", o máximo é "
						+ offer.maxPerOrder() + " por pedido.", Map.of("batchId", offer.batchId(), "max", offer.maxPerOrder()));
			}
			if (!offer.onSale() || offer.remaining() < quantity) {
				throw unavailable(offer);
			}
			long unitFee = fees.feeFor(offer.priceCents());
			lines.add(new Line(offer, quantity, offer.priceCents(), unitFee));
			subtotal += offer.priceCents() * quantity;
			fee += unitFee * quantity;
		}
		return new Quote(event, lines, subtotal, fee, subtotal + fee);
	}

	private EventRef eventOnSale(String slug) {
		EventRef event = events.findPublic(slug)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Evento não encontrado."));
		if (event.status() != EventStatus.PUBLISHED || !clock.instant().isBefore(event.endsAt())) {
			throw OrderRuleException.conflict("sales-closed", "As vendas dessa festa estão encerradas.");
		}
		return event;
	}

	private Placed existing(String keyHash, String eventSlug) {
		return orders.findByAccessKeyHash(keyHash).map(order -> {
			EventRef event = events.findPublic(eventSlug).orElse(null);
			if (event == null || !event.id().equals(order.getEventId())) {
				throw OrderRuleException.conflict("idempotency-key-reused",
						"Essa chave já foi usada em outro pedido. Gere uma nova.");
			}
			return placed(order, false);
		}).orElse(null);
	}

	private Placed placed(Order order, boolean created) {
		List<OrderItem> orderItems = items.findByOrderIdOrderByPosition(order.getId());
		Map<UUID, Offer> offers = inventory.offers(order.getEventId(),
				orderItems.stream().map(OrderItem::getTicketBatchId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(Offer::batchId, Function.identity()));
		EventRef event = events.find(order.getOrganizationId(), order.getEventId()).orElseThrow();
		return new Placed(event, order, orderItems, offers, created);
	}

	private static String validCpf(String value, String field) {
		if (!Cpf.isValid(value)) {
			throw rule("invalid-cpf", "CPF inválido.", Map.of("field", field));
		}
		return Cpf.digits(value);
	}

	private static String phone(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.replaceAll("[^0-9+]", "");
	}

	private static OrderRuleException unavailable(Offer offer) {
		return OrderRuleException.conflict("batch-unavailable",
				"O " + offer.batchName() + " de " + offer.typeName() + " não tem mais essa quantidade disponível.",
				Map.of("batchId", offer.batchId()));
	}

	private static OrderRuleException rule(String code, String message) {
		return OrderRuleException.rule(code, message);
	}

	private static OrderRuleException rule(String code, String message, Map<String, Object> properties) {
		return OrderRuleException.rule(code, message, properties);
	}

	private static ApiException orderNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado", "Pedido não encontrado.");
	}

}
