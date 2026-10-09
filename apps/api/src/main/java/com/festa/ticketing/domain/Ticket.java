package com.festa.ticketing.domain;

import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Ingresso emitido depois do pagamento, um por item do pedido (CLAUDE.md regra 6). O QR carrega um token
 * opaco ({@link TicketTokens}); aqui ficam só o nonce que o gera e o hash dele.
 */
@Entity
@Table(name = "tickets")
public class Ticket extends BaseEntity {

	public enum Status {
		VALID, CHECKED_IN, TRANSFERRED, CANCELLED
	}

	private UUID orderItemId;

	private UUID orderId;

	private UUID eventId;

	private UUID organizationId;

	private UUID ticketBatchId;

	private String buyerEmail;

	private String holderName;

	private byte[] holderCpfEncrypted;

	private String holderCpfHash;

	@Column(name = "is_half_price")
	private boolean halfPrice;

	private byte[] tokenNonce;

	private String tokenHash;

	@Enumerated(EnumType.STRING)
	private Status status = Status.VALID;

	protected Ticket() {
	}

	/** Dados do titular congelados do pedido, com CPF já cifrado. */
	public record Holder(String name, byte[] cpfEncrypted, String cpfHash, boolean halfPrice) {
	}

	public Ticket(UUID orderItemId, UUID orderId, UUID eventId, UUID organizationId, UUID ticketBatchId,
			String buyerEmail, Holder holder, byte[] tokenNonce, String tokenHash) {
		this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
		this.orderId = Objects.requireNonNull(orderId, "orderId");
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
		this.ticketBatchId = Objects.requireNonNull(ticketBatchId, "ticketBatchId");
		this.buyerEmail = Objects.requireNonNull(buyerEmail, "buyerEmail").trim().toLowerCase();
		this.holderName = Objects.requireNonNull(holder.name(), "holder.name");
		this.holderCpfEncrypted = Objects.requireNonNull(holder.cpfEncrypted(), "holder.cpfEncrypted");
		this.holderCpfHash = Objects.requireNonNull(holder.cpfHash(), "holder.cpfHash");
		this.halfPrice = holder.halfPrice();
		this.tokenNonce = Objects.requireNonNull(tokenNonce, "tokenNonce");
		this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
	}

	public UUID getOrderItemId() {
		return orderItemId;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public UUID getTicketBatchId() {
		return ticketBatchId;
	}

	public String getBuyerEmail() {
		return buyerEmail;
	}

	public String getHolderName() {
		return holderName;
	}

	public byte[] getHolderCpfEncrypted() {
		return holderCpfEncrypted;
	}

	public boolean isHalfPrice() {
		return halfPrice;
	}

	public byte[] getTokenNonce() {
		return tokenNonce;
	}

	public Status getStatus() {
		return status;
	}

}
