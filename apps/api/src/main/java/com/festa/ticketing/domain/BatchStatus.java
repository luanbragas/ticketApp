package com.festa.ticketing.domain;

/**
 * Ciclo de vida do lote (ADR-006). Só anda para a frente: esgotado e encerrado não reabrem.
 *
 * <pre>
 * SCHEDULED → ON_SALE → SOLD_OUT
 *     ↓          ↓
 *   CLOSED ←─────┘
 * </pre>
 */
public enum BatchStatus {

	SCHEDULED, ON_SALE, SOLD_OUT, CLOSED;

	public boolean isFinal() {
		return this == SOLD_OUT || this == CLOSED;
	}

	boolean canMoveTo(BatchStatus next) {
		return switch (this) {
			case SCHEDULED -> next == ON_SALE || next == CLOSED;
			case ON_SALE -> next == SOLD_OUT || next == CLOSED;
			case SOLD_OUT, CLOSED -> false;
		};
	}

}
