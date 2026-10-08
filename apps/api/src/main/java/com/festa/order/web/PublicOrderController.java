package com.festa.order.web;

import com.festa.order.app.CheckoutService;
import com.festa.order.app.CheckoutService.Buyer;
import com.festa.order.app.CheckoutService.Placed;
import com.festa.order.app.CheckoutService.PlaceOrder;
import com.festa.order.app.CheckoutService.Quote;
import com.festa.order.app.CheckoutService.TicketRequest;
import com.festa.order.domain.HalfPriceReason;
import com.festa.order.domain.Order;
import com.festa.order.domain.OrderItem;
import com.festa.order.domain.OrderRuleException;
import com.festa.order.domain.OrderStatus;
import com.festa.shared.crypto.PersonalDataCipher;
import com.festa.shared.text.Cpf;
import com.festa.shared.web.ApiException;
import com.festa.ticketing.api.Inventory.Offer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Compra sem cadastro (ADR-007). O navegador gera o {@code Idempotency-Key}: ele evita pedido em dobro
 * no clique repetido e é o segredo para acompanhar o pedido depois ({@code X-Order-Key}).
 */
@RestController
@RequestMapping("/api/v1/public")
class PublicOrderController {

	private final CheckoutService checkout;
	private final PersonalDataCipher cipher;

	PublicOrderController(CheckoutService checkout, PersonalDataCipher cipher) {
		this.checkout = checkout;
		this.cipher = cipher;
	}

	record SelectionItem(@NotNull UUID batchId,
			@NotNull @Min(value = 1, message = "Mínimo de 1.") @Max(value = 20, message = "Máximo de 20.") Integer quantity) {
	}

	record QuoteRequest(@NotEmpty(message = "Escolha pelo menos um ingresso.")
			@Size(max = 20, message = "Lotes demais.") List<@Valid @NotNull SelectionItem> items) {
	}

	record QuoteLine(UUID batchId, String batchName, String typeName, boolean halfPrice, int quantity,
			long unitPriceCents, long unitFeeCents, long totalCents, int maxPerOrder) {
	}

	record QuoteResponse(List<QuoteLine> lines, long subtotalCents, long feeCents, long totalCents) {
	}

	record BuyerRequest(
			@NotBlank(message = "Informe seu nome.") @Size(max = 120, message = "Nome muito longo.") String name,
			@NotBlank(message = "Informe seu e-mail.") @Email(message = "E-mail inválido.")
			@Size(max = 254, message = "E-mail muito longo.") String email,
			@Pattern(regexp = "^$|^[0-9+()\\s-]{10,20}$", message = "Celular inválido.") String phone,
			@NotBlank(message = "Informe seu CPF.") @Size(max = 20, message = "CPF inválido.") String cpf) {
	}

	record TicketHolderRequest(@NotNull UUID batchId,
			@NotBlank(message = "Informe o nome do titular.") @Size(max = 120, message = "Nome muito longo.") String holderName,
			@NotBlank(message = "Informe o CPF do titular.") @Size(max = 20, message = "CPF inválido.") String holderCpf,
			HalfPriceReason halfPriceReason) {
	}

	record PlaceOrderRequest(
			@NotBlank @Size(max = 80) String event,
			@NotNull @Valid BuyerRequest buyer,
			@NotEmpty(message = "Escolha pelo menos um ingresso.")
			@Size(max = CheckoutService.MAX_TICKETS_PER_ORDER, message = "No máximo 20 ingressos por pedido.")
			List<@Valid @NotNull TicketHolderRequest> tickets,
			Boolean adultDeclared,
			@NotNull @AssertTrue(message = "É preciso aceitar os termos.") Boolean termsAccepted) {
	}

	record ItemResponse(String batchName, String typeName, long unitPriceCents, long feeCents, boolean halfPrice,
			HalfPriceReason halfPriceReason, String holderName, String holderCpf) {
	}

	record BuyerResponse(String name, String email, String cpf) {
	}

	record OrderResponse(UUID id, OrderStatus status, Instant expiresAt, BuyerResponse buyer,
			List<ItemResponse> items, long subtotalCents, long feeCents, long totalCents) {
	}

	@PostMapping("/events/{slug}/quote")
	QuoteResponse quote(@PathVariable String slug, @Valid @RequestBody QuoteRequest body) {
		Map<UUID, Integer> selection = new LinkedHashMap<>();
		body.items().forEach(item -> selection.merge(item.batchId(), item.quantity(), Integer::sum));
		Quote quote = translate(() -> checkout.quote(slug, selection));
		return new QuoteResponse(quote.lines().stream()
			.map(l -> new QuoteLine(l.offer().batchId(), l.offer().batchName(), l.offer().typeName(),
					l.offer().halfPrice(), l.quantity(), l.unitPriceCents(), l.unitFeeCents(), l.totalCents(),
					l.offer().maxPerOrder()))
			.toList(), quote.subtotalCents(), quote.feeCents(), quote.totalCents());
	}

	@PostMapping("/orders")
	ResponseEntity<OrderResponse> place(
			@RequestHeader(name = "Idempotency-Key", required = false) String key,
			@Valid @RequestBody PlaceOrderRequest body) {
		String accessKey = requireKey(key, "Idempotency-Key");
		Placed placed = translate(() -> checkout.place(new PlaceOrder(body.event(), accessKey,
				new Buyer(body.buyer().name(), body.buyer().email(), body.buyer().phone(), body.buyer().cpf()),
				body.tickets().stream()
					.map(t -> new TicketRequest(t.batchId(), t.holderName(), t.holderCpf(), t.halfPriceReason()))
					.toList(),
				Boolean.TRUE.equals(body.adultDeclared()), Boolean.TRUE.equals(body.termsAccepted()))));
		return ResponseEntity.status(placed.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.cacheControl(CacheControl.noStore())
			.body(response(placed));
	}

	/** Status do pedido para a tela de pagamento. Sem a chave certa, responde como se não existisse. */
	@GetMapping("/orders/{orderId}")
	ResponseEntity<OrderResponse> get(@PathVariable UUID orderId,
			@RequestHeader(name = "X-Order-Key", required = false) String key) {
		Placed placed = checkout.find(orderId, requireKey(key, "X-Order-Key"));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response(placed));
	}

	private OrderResponse response(Placed placed) {
		Order order = placed.order();
		return new OrderResponse(order.getId(), order.getStatus(), order.getExpiresAt(),
				new BuyerResponse(order.getBuyerName(), order.getBuyerEmail(),
						Cpf.mask(cipher.decrypt(order.getBuyerCpfEncrypted()))),
				placed.items().stream().map(item -> item(item, placed.offers().get(item.getTicketBatchId()))).toList(),
				order.getSubtotalCents(), order.getFeeCents(), order.getTotalCents());
	}

	private ItemResponse item(OrderItem item, Offer offer) {
		return new ItemResponse(offer == null ? "Lote" : offer.batchName(), offer == null ? "" : offer.typeName(),
				item.getUnitPriceCents(), item.getFeeCents(), item.isHalfPrice(), item.getHalfPriceReason(),
				item.getHolderName(), Cpf.mask(cipher.decrypt(item.getHolderCpfEncrypted())));
	}

	/** Chave aleatória do navegador (crypto.randomUUID tem 36 caracteres). */
	private static String requireKey(String key, String header) {
		if (key == null || key.length() < 32 || key.length() > 100 || !key.matches("^[A-Za-z0-9_-]+$")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-order-key", "Chave do pedido inválida",
				"Envie o header " + header + " com 32 a 100 caracteres aleatórios.");
		}
		return key;
	}

	private static <T> T translate(Supplier<T> action) {
		try {
			return action.get();
		}
		catch (OrderRuleException ex) {
			ApiException api = new ApiException(ex.isConflict() ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_CONTENT,
				ex.getCode(), ex.isConflict() ? "Não deu para reservar" : "Confira o pedido", ex.getMessage());
			ex.getProperties().forEach(api::withProperty);
			throw api;
		}
	}

}
