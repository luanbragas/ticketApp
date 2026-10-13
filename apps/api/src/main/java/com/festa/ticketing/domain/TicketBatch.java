package com.festa.ticketing.domain;

import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lote de um tipo de ingresso: preço, capacidade e janela de venda. O status só anda pela virada
 * ({@link BatchRollover}) ou pelo encerramento manual; {@code sold} e {@code reserved} só mudam por SQL
 * atômico (ARCHITECTURE.md §Estoque), por isso a entidade nunca os grava.
 */
@Entity
@Table(name = "ticket_batches")
public class TicketBatch extends BaseEntity {

	public static final int NAME_MAX_LENGTH = 60;
	public static final long MAX_PRICE_CENTS = 100_000_000L;
	public static final int MAX_CAPACITY = 100_000;
	/** Limite por pedido quando o lote não define um. */
	public static final int DEFAULT_MAX_PER_ORDER = 10;

	private UUID ticketTypeId;

	private UUID eventId;

	private UUID organizationId;

	private String name;

	private long priceCents;

	private int capacity;

	@Column(insertable = false, updatable = false)
	private int sold;

	@Column(insertable = false, updatable = false)
	private int reserved;

	private Instant salesStartAt;

	private Instant salesEndAt;

	private Short maxPerOrder;

	private boolean visible = true;

	@Enumerated(EnumType.STRING)
	private BatchStatus status = BatchStatus.SCHEDULED;

	private short position;

	protected TicketBatch() {
	}

	/** Dados editáveis do lote. Edição troca tudo (PUT): data nula = sem data. */
	public record Terms(String name, long priceCents, int capacity, Instant salesStartAt, Instant salesEndAt,
			Integer maxPerOrder, boolean visible) {
	}

	public TicketBatch(TicketType type, Terms terms, int position) {
		this.ticketTypeId = type.getId();
		this.eventId = type.getEventId();
		this.organizationId = type.getOrganizationId();
		this.position = (short) position;
		apply(terms);
	}

	/**
	 * Edita o lote. Esgotado ou encerrado não muda mais (não reabre); capacidade nunca fica abaixo do que
	 * já foi vendido ou reservado.
	 */
	public void update(Terms terms) {
		if (status.isFinal()) {
			throw TicketRuleException.conflict("batch-closed", "Lote encerrado não pode ser alterado.");
		}
		apply(terms);
	}

	/** Encerramento manual: a virada abre o próximo lote do tipo. */
	public void close() {
		if (status.isFinal()) {
			throw TicketRuleException.conflict("batch-closed", "Esse lote já está encerrado.");
		}
		status = BatchStatus.CLOSED;
	}

	/** Lote com venda ou reserva não pode sumir: o pedido aponta para ele. */
	public void requireRemovable() {
		if (sold + reserved > 0) {
			throw TicketRuleException.conflict("batch-has-sales",
				"Esse lote já tem ingressos vendidos ou reservados. Encerre em vez de excluir.");
		}
	}

	/** Usado pela virada; só aceita passo para a frente. */
	public void moveTo(BatchStatus next) {
		if (!status.canMoveTo(next)) {
			throw new IllegalStateException("lote " + getId() + ": " + status + " → " + next);
		}
		status = next;
	}

	public BatchRollover.Slot slot() {
		return new BatchRollover.Slot(getId(), status, position, capacity, sold, salesStartAt, salesEndAt);
	}

	public int getRemaining() {
		return capacity - sold - reserved;
	}

	public int getEffectiveMaxPerOrder() {
		return maxPerOrder == null ? DEFAULT_MAX_PER_ORDER : maxPerOrder;
	}

	private void apply(Terms terms) {
		String newName = Objects.requireNonNull(terms.name(), "name").trim();
		if (newName.isEmpty() || newName.length() > NAME_MAX_LENGTH) {
			throw new IllegalArgumentException("nome inválido");
		}
		if (terms.priceCents() < 1 || terms.priceCents() > MAX_PRICE_CENTS) {
			throw TicketRuleException.rule("invalid-price", "Preço inválido.");
		}
		if (terms.capacity() < 1 || terms.capacity() > MAX_CAPACITY) {
			throw TicketRuleException.rule("invalid-capacity", "Quantidade inválida.");
		}
		if (terms.capacity() < sold + reserved) {
			throw TicketRuleException.rule("capacity-below-sold",
				"A quantidade não pode ficar abaixo de " + (sold + reserved) + ", que já foram vendidos ou reservados.");
		}
		if (terms.salesStartAt() != null && terms.salesEndAt() != null
				&& !terms.salesEndAt().isAfter(terms.salesStartAt())) {
			throw TicketRuleException.rule("invalid-sales-window", "A virada precisa ser depois da abertura.");
		}
		if (terms.maxPerOrder() != null && (terms.maxPerOrder() < 1 || terms.maxPerOrder() > 20)) {
			throw TicketRuleException.rule("invalid-max-per-order", "Use de 1 a 20 por pedido.");
		}
		name = newName;
		priceCents = terms.priceCents();
		capacity = terms.capacity();
		salesStartAt = terms.salesStartAt();
		salesEndAt = terms.salesEndAt();
		maxPerOrder = terms.maxPerOrder() == null ? null : terms.maxPerOrder().shortValue();
		visible = terms.visible();
	}

	public UUID getTicketTypeId() {
		return ticketTypeId;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public String getName() {
		return name;
	}

	public long getPriceCents() {
		return priceCents;
	}

	public int getCapacity() {
		return capacity;
	}

	public int getSold() {
		return sold;
	}

	public int getReserved() {
		return reserved;
	}

	public Instant getSalesStartAt() {
		return salesStartAt;
	}

	public Instant getSalesEndAt() {
		return salesEndAt;
	}

	public Integer getMaxPerOrder() {
		return maxPerOrder == null ? null : maxPerOrder.intValue();
	}

	public boolean isVisible() {
		return visible;
	}

	public BatchStatus getStatus() {
		return status;
	}

	public int getPosition() {
		return position;
	}

}
