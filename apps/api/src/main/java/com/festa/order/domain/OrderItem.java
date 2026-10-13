package com.festa.order.domain;

import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/** Um ingresso do pedido, com titular. Vira ingresso de verdade (com QR) só depois do pagamento (M6). */
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

	private UUID orderId;

	private UUID ticketBatchId;

	private long unitPriceCents;

	private long feeCents;

	private String holderName;

	private byte[] holderCpfEncrypted;

	private String holderCpfHash;

	@Column(name = "is_half_price")
	private boolean halfPrice;

	@Enumerated(EnumType.STRING)
	private HalfPriceReason halfPriceReason;

	private short position;

	protected OrderItem() {
	}

	/** Titular já validado, com CPF protegido por quem chama. */
	public record Holder(String name, byte[] cpfEncrypted, String cpfHash) {
	}

	public OrderItem(UUID orderId, UUID ticketBatchId, long unitPriceCents, long feeCents, Holder holder,
			HalfPriceReason halfPriceReason, int position) {
		this.orderId = Objects.requireNonNull(orderId, "orderId");
		this.ticketBatchId = Objects.requireNonNull(ticketBatchId, "ticketBatchId");
		if (unitPriceCents < 1 || feeCents < 0) {
			throw new IllegalArgumentException("valor inválido");
		}
		this.unitPriceCents = unitPriceCents;
		this.feeCents = feeCents;
		this.holderName = Objects.requireNonNull(holder.name(), "holder.name").trim();
		this.holderCpfEncrypted = Objects.requireNonNull(holder.cpfEncrypted(), "holder.cpfEncrypted");
		this.holderCpfHash = Objects.requireNonNull(holder.cpfHash(), "holder.cpfHash");
		this.halfPrice = halfPriceReason != null;
		this.halfPriceReason = halfPriceReason;
		this.position = (short) position;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getTicketBatchId() {
		return ticketBatchId;
	}

	public long getUnitPriceCents() {
		return unitPriceCents;
	}

	public long getFeeCents() {
		return feeCents;
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

	public HalfPriceReason getHalfPriceReason() {
		return halfPriceReason;
	}

	public int getPosition() {
		return position;
	}

}
