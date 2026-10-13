package com.festa.ticketing.app;

import com.festa.event.api.EventDirectory;
import com.festa.event.api.EventDirectory.EventRef;
import com.festa.shared.crypto.PersonalDataCipher;
import com.festa.shared.text.Cpf;
import com.festa.shared.web.ApiException;
import com.festa.ticketing.domain.Ticket;
import com.festa.ticketing.domain.TicketBatch;
import com.festa.ticketing.domain.TicketTokens;
import com.festa.ticketing.domain.TicketType;
import com.festa.ticketing.infra.TicketBatchRepository;
import com.festa.ticketing.infra.TicketRepository;
import com.festa.ticketing.infra.TicketTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leitura de ingressos para o comprador: pelo token (página do QR) e pelo e-mail verificado ("Meus
 * ingressos"). CPF sai sempre mascarado; o token é remontado pela chave, nunca lido do banco.
 */
@Service
public class TicketQueries {

	private final TicketRepository tickets;
	private final TicketBatchRepository batches;
	private final TicketTypeRepository types;
	private final EventDirectory events;
	private final TicketTokens tokens;
	private final PersonalDataCipher cipher;

	TicketQueries(TicketRepository tickets, TicketBatchRepository batches, TicketTypeRepository types,
			EventDirectory events, TicketTokens tokens, PersonalDataCipher cipher) {
		this.tickets = tickets;
		this.batches = batches;
		this.types = types;
		this.events = events;
		this.tokens = tokens;
		this.cipher = cipher;
	}

	/** O que a tela e o e-mail mostram de um ingresso. */
	public record TicketView(String token, Ticket.Status status, String holderName, String holderCpf,
			boolean halfPrice, String typeName, String batchName, EventRef event) {
	}

	/** Página do ingresso. Token inválido ou de ingresso inexistente responde igual: 404. */
	@Transactional(readOnly = true)
	public TicketView byToken(String token) {
		if (!TicketTokens.looksValid(token)) {
			throw notFound();
		}
		Ticket ticket = tickets.findByTokenHash(TicketTokens.hash(token)).orElseThrow(TicketQueries::notFound);
		return views(List.of(ticket)).getFirst();
	}

	/** Ingressos de um pedido, para o e-mail de confirmação. */
	@Transactional(readOnly = true)
	public List<TicketView> forOrder(UUID orderId) {
		return views(tickets.findByOrderIdOrderByCreatedAt(orderId));
	}

	/** "Meus ingressos": só com e-mail já verificado (quem chama confere). */
	@Transactional(readOnly = true)
	public List<TicketView> forBuyer(String verifiedEmail) {
		return views(tickets.findByBuyerEmailOrderByCreatedAt(verifiedEmail.trim().toLowerCase()));
	}

	private List<TicketView> views(List<Ticket> found) {
		if (found.isEmpty()) {
			return List.of();
		}
		Map<UUID, TicketBatch> batchById = batches.findAllById(ids(found, Ticket::getTicketBatchId)).stream()
			.collect(Collectors.toMap(TicketBatch::getId, Function.identity()));
		Map<UUID, TicketType> typeById = types.findAllById(ids(batchById.values(), TicketBatch::getTicketTypeId)).stream()
			.collect(Collectors.toMap(TicketType::getId, Function.identity()));
		Map<UUID, EventRef> eventById = new HashMap<>();
		return found.stream().map(ticket -> {
			TicketBatch batch = batchById.get(ticket.getTicketBatchId());
			EventRef event = eventById.computeIfAbsent(ticket.getEventId(),
					id -> events.find(ticket.getOrganizationId(), id).orElseThrow());
			return new TicketView(tokens.tokenFor(ticket.getTokenNonce()), ticket.getStatus(), ticket.getHolderName(),
					Cpf.mask(cipher.decrypt(ticket.getHolderCpfEncrypted())), ticket.isHalfPrice(),
					typeById.get(batch.getTicketTypeId()).getName(), batch.getName(), event);
		}).toList();
	}

	private static <T> List<UUID> ids(Collection<T> items, Function<T, UUID> id) {
		return items.stream().map(id).distinct().toList();
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado", "Ingresso não encontrado.");
	}

}
