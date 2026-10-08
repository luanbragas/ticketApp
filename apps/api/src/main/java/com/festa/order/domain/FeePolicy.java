package com.festa.order.domain;

/**
 * Taxa de serviço por ingresso, paga pelo comprador por cima do preço (ADR-007).
 *
 * @param basisPoints percentual em pontos-base (1000 = 10%)
 * @param minimumCents piso por ingresso, em centavos (0 = sem piso)
 */
public record FeePolicy(int basisPoints, long minimumCents) {

	public FeePolicy {
		if (basisPoints < 0 || basisPoints > 5000) {
			throw new IllegalArgumentException("taxa fora do intervalo 0–50%: " + basisPoints);
		}
		if (minimumCents < 0) {
			throw new IllegalArgumentException("mínimo negativo: " + minimumCents);
		}
	}

	/** Taxa de um ingresso: percentual arredondado para o centavo mais próximo (meio centavo sobe), com piso. */
	public long feeFor(long priceCents) {
		long percent = (priceCents * basisPoints + 5_000) / 10_000;
		return Math.max(percent, minimumCents);
	}

}
