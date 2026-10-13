package com.festa.ticketing.api;

import java.util.List;
import java.util.UUID;

/**
 * Entrada na portaria para o módulo de check-in (ARCHITECTURE.md §Check-in). O status do ingresso é do
 * ticketing: validar é um {@code UPDATE} condicional atômico (VALID → CHECKED_IN), então dois leitores do
 * mesmo QR nunca deixam entrar duas vezes. Métodos de escrita entram na transação de quem chama.
 */
public interface Admission {

	enum Outcome {
		/** Entrou agora. */
		ADMITTED,
		/** Já tinha entrado. */
		ALREADY_IN,
		/** Não é deste evento, não existe, foi cancelado ou transferido. */
		NOT_VALID
	}

	/** O que a portaria vê do ingresso: nome, tipo e status. Sem CPF (SECURITY.md). */
	record Pass(UUID ticketId, String holderName, String typeName, String batchName, boolean halfPrice,
			String status) {
	}

	/** {@code pass} é nulo quando o ingresso não existe neste evento. */
	record Result(Outcome outcome, Pass pass) {
	}

	/** Linha da lista offline: o aparelho confere o hash do token lido sem falar com o servidor. */
	record ManifestEntry(UUID ticketId, String tokenHash, String holderName, String typeName, boolean halfPrice,
			String status) {
	}

	/** Participante para a lista do painel (CPF mascarado). */
	record Attendee(Pass pass, String holderCpf, String buyerEmail) {
	}

	record Counts(int issued, int checkedIn) {
	}

	/** Pelo token lido do QR. */
	Result admitByToken(UUID eventId, String token);

	/** Pelo hash do token (sincronização do modo offline, que só guarda o hash). */
	Result admitByTokenHash(UUID eventId, String tokenHash);

	/** Busca manual na portaria. */
	Result admitById(UUID eventId, UUID ticketId);

	/** Desfazer check-in: CHECKED_IN → VALID. Falso se o ingresso não estava com check-in. */
	boolean readmit(UUID eventId, UUID ticketId);

	List<ManifestEntry> manifest(UUID eventId);

	/**
	 * Por nome (parte do nome, sem diferenciar maiúsculas) ou CPF completo do titular.
	 *
	 * @param status nulo = todos
	 */
	List<Attendee> attendees(UUID eventId, String query, String status, int limit);

	Counts counts(UUID eventId);

}
