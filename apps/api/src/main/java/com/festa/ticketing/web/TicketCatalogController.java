package com.festa.ticketing.web;

import com.festa.shared.security.AuthenticatedUser;
import com.festa.ticketing.app.TicketingService;
import com.festa.ticketing.app.TicketingService.BatchView;
import com.festa.ticketing.app.TicketingService.Catalog;
import com.festa.ticketing.domain.BatchStatus;
import com.festa.ticketing.domain.HalfPriceQuota;
import com.festa.ticketing.domain.TicketBatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tipos e lotes do evento no painel. Toda escrita devolve o catálogo inteiro já com a virada aplicada,
 * para a tela trocar o estado de uma vez.
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/events/{eventId}")
class TicketCatalogController {

	private final TicketingService ticketing;

	TicketCatalogController(TicketingService ticketing) {
		this.ticketing = ticketing;
	}

	record CreateTypeRequest(
			@NotBlank(message = "Informe o nome do ingresso.") @Size(max = 60, message = "Nome muito longo.") String name,
			@Size(max = 500, message = "Descrição muito longa.") String description,
			Boolean halfPrice) {
	}

	/** Campos nulos ficam como estão; descrição vazia apaga. Meia ou inteira não muda depois de criado. */
	record UpdateTypeRequest(
			@Pattern(regexp = ".*\\S.*", message = "O nome não pode ficar vazio.")
			@Size(max = 60, message = "Nome muito longo.") String name,
			@Size(max = 500, message = "Descrição muito longa.") String description) {
	}

	/** Lote inteiro (criação e edição): data nula = sem data. */
	record BatchRequest(
			@NotBlank(message = "Informe o nome do lote.") @Size(max = 60, message = "Nome muito longo.") String name,
			@NotNull(message = "Informe o preço.") @Min(value = 1, message = "Preço mínimo de R$ 0,01.")
			@Max(value = TicketBatch.MAX_PRICE_CENTS, message = "Preço muito alto.") Long priceCents,
			@NotNull(message = "Informe a quantidade.") @Min(value = 1, message = "Mínimo de 1 ingresso.")
			@Max(value = TicketBatch.MAX_CAPACITY, message = "Quantidade muito alta.") Integer capacity,
			Instant salesStartAt,
			Instant salesEndAt,
			@Min(value = 1, message = "Use de 1 a 20.") @Max(value = 20, message = "Use de 1 a 20.") Integer maxPerOrder,
			Boolean visible) {

		TicketBatch.Terms terms() {
			return new TicketBatch.Terms(name, priceCents, capacity, salesStartAt, salesEndAt, maxPerOrder,
				visible == null || visible);
		}

	}

	record BatchResponse(UUID id, String name, long priceCents, int capacity, int sold, int reserved, int remaining,
			Instant salesStartAt, Instant salesEndAt, Integer maxPerOrder, boolean visible, BatchStatus status) {
	}

	record TypeResponse(UUID id, String name, String description, boolean halfPrice, List<BatchResponse> batches) {
	}

	record QuotaResponse(int percent, int total, int halfPrice, int minimum, boolean met) {
	}

	record CatalogResponse(List<TypeResponse> types, QuotaResponse halfPriceQuota) {
	}

	@GetMapping("/ticket-types")
	CatalogResponse catalog(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId) {
		return response(ticketing.catalog(orgId, eventId, user.id()));
	}

	@PostMapping("/ticket-types")
	ResponseEntity<CatalogResponse> createType(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID orgId, @PathVariable UUID eventId, @Valid @RequestBody CreateTypeRequest body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(response(
				ticketing.createType(orgId, eventId, user.id(), body.name(), body.description(),
						Boolean.TRUE.equals(body.halfPrice()))));
	}

	@PatchMapping("/ticket-types/{typeId}")
	CatalogResponse updateType(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId, @PathVariable UUID typeId, @Valid @RequestBody UpdateTypeRequest body) {
		return response(ticketing.updateType(orgId, eventId, typeId, user.id(), body.name(), body.description()));
	}

	@DeleteMapping("/ticket-types/{typeId}")
	CatalogResponse deleteType(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId, @PathVariable UUID typeId) {
		return response(ticketing.deleteType(orgId, eventId, typeId, user.id()));
	}

	@PostMapping("/ticket-types/{typeId}/batches")
	ResponseEntity<CatalogResponse> createBatch(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID orgId, @PathVariable UUID eventId, @PathVariable UUID typeId,
			@Valid @RequestBody BatchRequest body) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(response(ticketing.createBatch(orgId, eventId, typeId, user.id(), body.terms())));
	}

	@PutMapping("/batches/{batchId}")
	CatalogResponse updateBatch(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId, @PathVariable UUID batchId, @Valid @RequestBody BatchRequest body) {
		return response(ticketing.updateBatch(orgId, eventId, batchId, user.id(), body.terms()));
	}

	@PostMapping("/batches/{batchId}/close")
	CatalogResponse closeBatch(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId, @PathVariable UUID batchId) {
		return response(ticketing.closeBatch(orgId, eventId, batchId, user.id()));
	}

	@DeleteMapping("/batches/{batchId}")
	CatalogResponse deleteBatch(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId, @PathVariable UUID batchId) {
		return response(ticketing.deleteBatch(orgId, eventId, batchId, user.id()));
	}

	private static CatalogResponse response(Catalog catalog) {
		HalfPriceQuota q = catalog.quota();
		return new CatalogResponse(
				catalog.types().stream()
					.map(t -> new TypeResponse(t.type().getId(), t.type().getName(), t.type().getDescription(),
							t.type().isHalfPrice(), t.batches().stream().map(TicketCatalogController::batch).toList()))
					.toList(),
				new QuotaResponse(q.percent(), q.total(), q.halfPrice(), q.minimum(), q.met()));
	}

	private static BatchResponse batch(BatchView view) {
		TicketBatch b = view.batch();
		return new BatchResponse(b.getId(), b.getName(), b.getPriceCents(), b.getCapacity(), b.getSold(),
			b.getReserved(), b.getRemaining(), b.getSalesStartAt(), b.getSalesEndAt(), b.getMaxPerOrder(),
			b.isVisible(), view.status());
	}

}
