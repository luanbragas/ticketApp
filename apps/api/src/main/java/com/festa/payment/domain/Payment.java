package com.festa.payment.domain;

import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma tentativa de cobrança de um pedido no gateway. O status espelha o do gateway e só muda pela conciliação
 * do webhook (CLAUDE.md regra 4); quem decide o pedido é o módulo de pedidos.
 */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

	public enum Provider {
		MERCADO_PAGO, FAKE
	}

	public enum Method {
		PIX, CARD
	}

	public enum Status {
		PENDING, APPROVED, REJECTED, CANCELLED, REFUNDED, CHARGED_BACK;

		public boolean isFinal() {
			return this != PENDING;
		}
	}

	private UUID orderId;

	private UUID organizationId;

	@Enumerated(EnumType.STRING)
	private Provider provider;

	private String providerPaymentId;

	private String idempotencyKey;

	@Enumerated(EnumType.STRING)
	private Method method;

	@Enumerated(EnumType.STRING)
	private Status status;

	private String statusDetail;

	private long amountCents;

	private long platformFeeCents;

	private String pixQrCode;

	private String pixQrBase64;

	private Instant pixExpiresAt;

	private Instant approvedAt;

	@JdbcTypeCode(SqlTypes.JSON)
	private String rawPayload;

	protected Payment() {
	}

	public Payment(UUID orderId, UUID organizationId, Provider provider, String providerPaymentId,
			String idempotencyKey, Method method, long amountCents, long platformFeeCents) {
		this.orderId = Objects.requireNonNull(orderId, "orderId");
		this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
		this.provider = Objects.requireNonNull(provider, "provider");
		this.providerPaymentId = Objects.requireNonNull(providerPaymentId, "providerPaymentId");
		this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
		this.method = Objects.requireNonNull(method, "method");
		this.amountCents = amountCents;
		this.platformFeeCents = platformFeeCents;
		this.status = Status.PENDING;
	}

	public void attachPix(String qrCode, String qrBase64, Instant expiresAt) {
		this.pixQrCode = qrCode;
		this.pixQrBase64 = qrBase64;
		this.pixExpiresAt = expiresAt;
	}

	/**
	 * Novo retrato vindo do gateway. Aprovado não volta para pendente; estorno e chargeback podem vir depois
	 * de aprovado.
	 */
	public void update(Status next, String detail, String raw, Instant now) {
		if (status == Status.APPROVED && next == Status.PENDING) {
			return;
		}
		if (next == Status.APPROVED && approvedAt == null) {
			approvedAt = now;
		}
		status = next;
		statusDetail = detail;
		rawPayload = raw;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public Provider getProvider() {
		return provider;
	}

	public String getProviderPaymentId() {
		return providerPaymentId;
	}

	public Method getMethod() {
		return method;
	}

	public Status getStatus() {
		return status;
	}

	public String getStatusDetail() {
		return statusDetail;
	}

	public long getAmountCents() {
		return amountCents;
	}

	public long getPlatformFeeCents() {
		return platformFeeCents;
	}

	public String getPixQrCode() {
		return pixQrCode;
	}

	public String getPixQrBase64() {
		return pixQrBase64;
	}

	public Instant getPixExpiresAt() {
		return pixExpiresAt;
	}

	public Instant getApprovedAt() {
		return approvedAt;
	}

}
