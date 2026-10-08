package com.festa.ticketing.domain;

import java.util.Collection;

/**
 * Cota de meia-entrada do evento (Lei 12.933/2013, ADR-006): pelo menos {@code percent}% dos ingressos
 * colocados à venda precisam ser de meia. Conta a capacidade dos lotes ainda abertos ou agendados e,
 * dos encerrados, só o que foi de fato vendido ou reservado (o resto nunca ficou disponível).
 *
 * @param percent cota configurada no evento (padrão 40)
 * @param total ingressos disponíveis no evento, inteira e meia
 * @param halfPrice desses, quantos são de tipo meia
 */
public record HalfPriceQuota(int percent, int total, int halfPrice) {

	public record Line(boolean halfPrice, BatchStatus status, int capacity, int sold, int reserved) {
	}

	public static HalfPriceQuota of(int percent, Collection<Line> batches) {
		int total = 0;
		int half = 0;
		for (Line line : batches) {
			int offered = line.status().isFinal() ? line.sold() + line.reserved() : line.capacity();
			total += offered;
			if (line.halfPrice()) {
				half += offered;
			}
		}
		return new HalfPriceQuota(percent, total, half);
	}

	/** Mínimo de ingressos de meia, arredondado para cima. */
	public int minimum() {
		return (total * percent + 99) / 100;
	}

	public boolean met() {
		return halfPrice >= minimum();
	}

}
