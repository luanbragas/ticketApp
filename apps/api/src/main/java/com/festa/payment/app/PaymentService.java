package com.festa.payment.app;

import com.festa.order.api.OrderBilling;
import com.festa.order.api.OrderBilling.BillableOrder;
import com.festa.order.api.OrderBilling.PaidOutcome;
import com.festa.payment.app.PaymentGateway.GatewayPayment;
import com.festa.payment.domain.Payment;
import com.festa.payment.infra.PaymentRepository;
import com.festa.shared.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cobrança e conciliação (ARCHITECTURE.md §Pagamento, ADR-011). O navegador só pede "pagar com PIX/cartão"; valor,
 * taxa e vendedor saem do pedido. Pago é decidido aqui, depois de consultar o gateway, e só a partir do webhook
 * validado (CLAUDE.md regra 4).
 */
@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	/**
	 * Validade do PIX. Mais que a reserva (10 min): quem paga depois que a reserva vence cai no caso de borda
	 * (reserva de novo se houver estoque, senão estorna).
	 */
	static final Duration PIX_TTL = Duration.ofMinutes(30);

	private final PaymentRepository payments;
	private final PaymentAccountService accounts;
	private final PaymentGateway gateway;
	private final OrderBilling orders;
	private final Clock clock;
	private final String notificationUrl;

	PaymentService(PaymentRepository payments, PaymentAccountService accounts, PaymentGateway gateway,
			OrderBilling orders, Clock clock, @Value("${festa.api.base-url}") String apiBaseUrl) {
		this.payments = payments;
		this.accounts = accounts;
		this.gateway = gateway;
		this.orders = orders;
		this.clock = clock;
		this.notificationUrl = apiBaseUrl + "/api/v1/webhooks/mercadopago";
	}

	/** Cartão tokenizado no navegador pelo SDK do gateway. */
	public record CardData(String token, String paymentMethodId, String issuerId) {
	}

	/** Pagamento do pedido, com o que a tela precisa (QR do PIX, status). */
	public record View(Payment payment, boolean simulated) {
	}

	/** Gera o PIX (ou devolve o que já existe e ainda vale) do pedido. */
	@Transactional
	public View payWithPix(UUID orderId, String accessKey) {
		BillableOrder order = payable(orderId, accessKey);
		Instant now = clock.instant();
		String key = "pix:" + orderId;
		Optional<Payment> existing = payments.findByIdempotencyKey(key);
		if (existing.isPresent()) {
			return view(existing.get());
		}
		GatewayPayment created = gateway.createPix(seller(order), new PaymentGateway.PixCharge(key, orderId.toString(),
				order.totalCents(), order.feeCents(), description(order),
				new PaymentGateway.Payer(order.buyerName(), order.buyerEmail()), now.plus(PIX_TTL), notificationUrl));
		Payment payment = new Payment(orderId, order.organizationId(), gateway.provider(), created.id(), key,
				Payment.Method.PIX, order.totalCents(), order.feeCents());
		payment.attachPix(created.pixQrCode(), created.pixQrBase64(),
				created.pixExpiresAt() != null ? created.pixExpiresAt() : now.plus(PIX_TTL));
		payment.update(created.status(), created.statusDetail(), created.raw(), now);
		return view(payments.save(payment));
	}

	/**
	 * Cartão: o gateway pode aprovar na hora, mas o pedido só vira pago na conciliação (o webhook chega em
	 * seguida). Cada tentativa tem sua chave de idempotência: recusado pode tentar de novo.
	 */
	@Transactional
	public View payWithCard(UUID orderId, String accessKey, CardData card) {
		BillableOrder order = payable(orderId, accessKey);
		Instant now = clock.instant();
		String key = "card:" + orderId + ":" + (payments.countByOrderIdAndMethod(orderId, Payment.Method.CARD) + 1);
		GatewayPayment created = gateway.createCard(seller(order), new PaymentGateway.CardCharge(key,
				orderId.toString(), order.totalCents(), order.feeCents(), description(order),
				new PaymentGateway.Payer(order.buyerName(), order.buyerEmail()), card.token(), card.paymentMethodId(),
				card.issuerId(), notificationUrl));
		Payment payment = new Payment(orderId, order.organizationId(), gateway.provider(), created.id(), key,
				Payment.Method.CARD, order.totalCents(), order.feeCents());
		payment.update(Payment.Status.PENDING, created.statusDetail(), created.raw(), now);
		payments.saveAndFlush(payment);
		if (gateway.provider() == Payment.Provider.FAKE) {
			// O simulado não manda webhook: roda a mesma conciliação que o aviso do gateway real dispararia.
			reconcile(created.id());
		}
		return view(payment);
	}

	/** Último pagamento do pedido, para a tela acompanhar. */
	@Transactional(readOnly = true)
	public Optional<View> current(UUID orderId, String accessKey) {
		orders.forBuyer(orderId, accessKey).orElseThrow(PaymentService::orderNotFound);
		return payments.findByOrderIdOrderByCreatedAtDesc(orderId).stream().findFirst().map(this::view);
	}

	/**
	 * Conciliação a partir do webhook já validado: consulta o gateway (não confia no aviso), confere valor e
	 * pedido, atualiza o pagamento e avisa o módulo de pedidos. Idempotente: o mesmo aviso pode chegar várias
	 * vezes, fora de ordem.
	 *
	 * @return falso se o pagamento não é desta plataforma (aviso ignorado)
	 */
	@Transactional
	public boolean reconcile(String providerPaymentId) {
		Optional<Payment> found = payments.lockByProviderId(gateway.provider(), providerPaymentId);
		if (found.isEmpty()) {
			log.info("Aviso de pagamento desconhecido ignorado: {}", providerPaymentId);
			return false;
		}
		Payment payment = found.get();
		PaymentGateway.Seller seller = accounts.seller(payment.getOrganizationId())
			.orElseThrow(() -> new IllegalStateException("organização sem conta de recebimento"));
		GatewayPayment remote = gateway.fetch(seller, providerPaymentId);
		if (remote.amountCents() != payment.getAmountCents()
				|| !payment.getOrderId().toString().equals(remote.externalReference())) {
			// Valor ou pedido diferentes do que cobramos: não libera nada e fica no log para análise.
			log.error("Pagamento {} não confere com o pedido {} (valor {} x {})", providerPaymentId,
					payment.getOrderId(), remote.amountCents(), payment.getAmountCents());
			return true;
		}
		Instant now = clock.instant();
		boolean newlyApproved = remote.status() == Payment.Status.APPROVED && payment.getStatus() != Payment.Status.APPROVED;
		payment.update(remote.status(), remote.statusDetail(), remote.raw(), now);
		if (newlyApproved) {
			PaidOutcome outcome = orders.paid(payment.getOrderId());
			if (outcome == PaidOutcome.NO_STOCK) {
				// Pagou depois da reserva vencer e não sobrou ingresso: devolve o dinheiro (ARCHITECTURE.md).
				gateway.refund(seller, providerPaymentId, "refund:" + payment.getId());
				payment.update(Payment.Status.REFUNDED, "late_payment_no_stock", remote.raw(), now);
				orders.refunded(payment.getOrderId());
				log.warn("Pedido {} pago depois de expirar e sem estoque: estornado", payment.getOrderId());
			}
		}
		return true;
	}

	private BillableOrder payable(UUID orderId, String accessKey) {
		BillableOrder order = orders.forBuyer(orderId, accessKey).orElseThrow(PaymentService::orderNotFound);
		if (!"PENDING_PAYMENT".equals(order.status())) {
			throw new ApiException(HttpStatus.CONFLICT, "order-not-payable", "Pedido não está aguardando pagamento",
				"PAID".equals(order.status()) ? "Esse pedido já foi pago." : "Esse pedido expirou. Escolha os ingressos de novo.");
		}
		if (!clock.instant().isBefore(order.expiresAt())) {
			throw new ApiException(HttpStatus.CONFLICT, "order-not-payable", "Reserva expirou",
				"A reserva expirou. Escolha os ingressos de novo.");
		}
		return order;
	}

	private PaymentGateway.Seller seller(BillableOrder order) {
		return accounts.seller(order.organizationId())
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "seller-not-connected", "Venda indisponível",
				"Essa festa ainda não está recebendo pagamentos. Fale com a organização."));
	}

	private View view(Payment payment) {
		return new View(payment, payment.getProvider() == Payment.Provider.FAKE);
	}

	private static String description(BillableOrder order) {
		String name = "Ingressos " + order.eventName();
		return name.length() > 60 ? name.substring(0, 60) : name;
	}

	private static ApiException orderNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado", "Pedido não encontrado.");
	}

}
