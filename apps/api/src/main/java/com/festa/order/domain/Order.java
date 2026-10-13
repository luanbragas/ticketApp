package com.festa.order.domain;

import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pedido: segura o estoque como reserva até o pagamento ou a expiração. Ingresso não nasce aqui
 * (CLAUDE.md regra 6). Valores em centavos, congelados na criação; status só muda pelos métodos de
 * transição.
 */
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

	private UUID eventId;

	private UUID organizationId;

	private String buyerName;

	private String buyerEmail;

	private String buyerPhone;

	private byte[] buyerCpfEncrypted;

	private String buyerCpfHash;

	private long subtotalCents;

	private long feeCents;

	private long discountCents;

	private long totalCents;

	@Enumerated(EnumType.STRING)
	private OrderStatus status = OrderStatus.PENDING_PAYMENT;

	private Instant expiresAt;

	private Instant paidAt;

	private Instant expiredAt;

	private String accessKeyHash;

	private boolean adultDeclared;

	private String termsVersion;

	private Instant termsAcceptedAt;

	protected Order() {
	}

	/** Comprador já validado e com CPF protegido (cifrado + hash) por quem chama. */
	public record Buyer(String name, String email, String phone, byte[] cpfEncrypted, String cpfHash) {
	}

	public Order(UUID eventId, UUID organizationId, Buyer buyer, long subtotalCents, long feeCents, Instant now,
			Instant expiresAt, String accessKeyHash, boolean adultDeclared, String termsVersion) {
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
		this.buyerName = Objects.requireNonNull(buyer.name(), "buyer.name").trim();
		this.buyerEmail = Objects.requireNonNull(buyer.email(), "buyer.email").trim().toLowerCase();
		this.buyerPhone = buyer.phone();
		this.buyerCpfEncrypted = Objects.requireNonNull(buyer.cpfEncrypted(), "buyer.cpfEncrypted");
		this.buyerCpfHash = Objects.requireNonNull(buyer.cpfHash(), "buyer.cpfHash");
		if (subtotalCents < 0 || feeCents < 0) {
			throw new IllegalArgumentException("valor negativo");
		}
		this.subtotalCents = subtotalCents;
		this.feeCents = feeCents;
		this.discountCents = 0;
		this.totalCents = subtotalCents + feeCents;
		if (!expiresAt.isAfter(now)) {
			throw new IllegalArgumentException("expiração precisa ser no futuro");
		}
		this.expiresAt = expiresAt;
		this.accessKeyHash = Objects.requireNonNull(accessKeyHash, "accessKeyHash");
		this.adultDeclared = adultDeclared;
		this.termsVersion = Objects.requireNonNull(termsVersion, "termsVersion");
		this.termsAcceptedAt = now;
	}

	/**
	 * Aguardando pagamento → pago. Só o webhook validado do gateway chega aqui (CLAUDE.md regra 4); quem
	 * chama transforma a reserva em venda e publica {@code OrderPaid} na mesma transação.
	 */
	public void markPaid(Instant now) {
		if (status != OrderStatus.PENDING_PAYMENT) {
			throw OrderRuleException.conflict("invalid-order-status", "Só pedido aguardando pagamento pode ser pago.");
		}
		status = OrderStatus.PAID;
		paidAt = now;
	}

	/** Aguardando pagamento → expirado. Quem chama devolve a reserva ao estoque na mesma transação. */
	public void expire(Instant now) {
		if (status != OrderStatus.PENDING_PAYMENT) {
			throw OrderRuleException.conflict("invalid-order-status", "Só pedido aguardando pagamento expira.");
		}
		if (now.isBefore(expiresAt)) {
			throw new IllegalStateException("pedido " + getId() + " ainda não venceu");
		}
		status = OrderStatus.EXPIRED;
		expiredAt = now;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public String getBuyerName() {
		return buyerName;
	}

	public String getBuyerEmail() {
		return buyerEmail;
	}

	public String getBuyerPhone() {
		return buyerPhone;
	}

	public byte[] getBuyerCpfEncrypted() {
		return buyerCpfEncrypted;
	}

	public long getSubtotalCents() {
		return subtotalCents;
	}

	public long getFeeCents() {
		return feeCents;
	}

	public long getDiscountCents() {
		return discountCents;
	}

	public long getTotalCents() {
		return totalCents;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public String getBuyerCpfHash() {
		return buyerCpfHash;
	}

	public String getAccessKeyHash() {
		return accessKeyHash;
	}

}
