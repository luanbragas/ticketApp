package com.festa.ticketing.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Virada de lote (ADR-006), por tipo de ingresso e na ordem dos lotes:
 * <ul>
 * <li>lote à venda vira quando esgota (vendidos pagos = capacidade) <b>ou</b> quando chega a data de
 * virada, o que vier primeiro;</li>
 * <li>o próximo lote abre na hora, a menos que tenha data de abertura no futuro — aí o tipo espera, e
 * os lotes de trás não passam na frente;</li>
 * <li>lote cuja data de virada passou sem abrir é encerrado;</li>
 * <li>esgotado e encerrado não reabrem.</li>
 * </ul>
 * Função pura: o serviço aplica o resultado nas entidades, e a página pública usa o mesmo cálculo
 * para mostrar o status certo entre uma rodada e outra do job.
 */
public final class BatchRollover {

	private BatchRollover() {
	}

	/** Foto do lote para o cálculo. */
	public record Slot(UUID id, BatchStatus status, int position, int capacity, int sold, Instant salesStartAt,
			Instant salesEndAt) {
	}

	/**
	 * Status que cada lote de <b>um</b> tipo deve ter em {@code now}. Só devolve os lotes que mudam.
	 */
	public static Map<UUID, BatchStatus> evaluate(List<Slot> batchesOfOneType, Instant now) {
		Map<UUID, BatchStatus> changes = new LinkedHashMap<>();
		List<Slot> ordered = batchesOfOneType.stream().sorted(Comparator.comparingInt(Slot::position)).toList();
		for (Slot slot : ordered) {
			if (slot.status().isFinal()) {
				continue;
			}
			if (slot.status() == BatchStatus.ON_SALE && slot.sold() >= slot.capacity()) {
				changes.put(slot.id(), BatchStatus.SOLD_OUT);
				continue;
			}
			if (slot.salesEndAt() != null && !now.isBefore(slot.salesEndAt())) {
				changes.put(slot.id(), BatchStatus.CLOSED);
				continue;
			}
			if (slot.status() == BatchStatus.SCHEDULED
					&& (slot.salesStartAt() == null || !now.isBefore(slot.salesStartAt()))) {
				changes.put(slot.id(), BatchStatus.ON_SALE);
			}
			// Este é o lote da vez (à venda ou esperando a data): os de trás continuam agendados.
			break;
		}
		return changes;
	}

}
