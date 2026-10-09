package com.festa.promoter.web;

import com.festa.promoter.app.PromoterService;
import com.festa.promoter.app.PromoterService.AddPromoter;
import com.festa.promoter.app.PromoterService.EventPromoters;
import com.festa.promoter.app.PromoterService.LinkView;
import com.festa.promoter.domain.Promoter;
import com.festa.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Promoters da organização e links por evento (PLAN.md M7). Promoter vê só o próprio link. */
@RestController
class PromoterController {

	private final PromoterService promoters;
	private final String webBaseUrl;

	PromoterController(PromoterService promoters, @Value("${festa.web.base-url}") String webBaseUrl) {
		this.promoters = promoters;
		this.webBaseUrl = webBaseUrl;
	}

	/** Promoter já cadastrado ({@code promoterId}) ou novo (nome, telefone e, se for da equipe, usuário). */
	record AddPromoterRequest(UUID promoterId,
			@Size(max = 80, message = "Nome muito longo.") String name,
			@Pattern(regexp = "^$|^[0-9+()\\s-]{10,20}$", message = "Telefone inválido.") String phone,
			UUID userId,
			@Size(max = 30, message = "Código muito longo.") String code) {
	}

	record SetActiveRequest(@NotNull Boolean active) {
	}

	record PromoterResponse(UUID id, String name, String phone, UUID userId) {
	}

	record LinkResponse(UUID id, UUID promoterId, String name, String phone, boolean teamMember, String code,
			String url, boolean active, int tickets, long revenueCents) {
	}

	record EventPromotersResponse(List<LinkResponse> links, int tickets, long revenueCents, boolean canManage) {
	}

	@GetMapping("/api/v1/orgs/{orgId}/promoters")
	List<PromoterResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId) {
		return promoters.list(orgId, user.id()).stream()
			.map(p -> new PromoterResponse(p.getId(), p.getName(), p.getPhone(), p.getUserId()))
			.toList();
	}

	@GetMapping("/api/v1/orgs/{orgId}/events/{eventId}/promoters")
	EventPromotersResponse forEvent(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId) {
		return response(promoters.forEvent(orgId, eventId, user.id()));
	}

	@PostMapping("/api/v1/orgs/{orgId}/events/{eventId}/promoters")
	ResponseEntity<EventPromotersResponse> add(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID orgId, @PathVariable UUID eventId, @Valid @RequestBody AddPromoterRequest body) {
		EventPromoters result = promoters.addToEvent(orgId, eventId, user.id(),
				new AddPromoter(body.promoterId(), body.name(), body.phone(), body.userId(), body.code()));
		return ResponseEntity.status(HttpStatus.CREATED).body(response(result));
	}

	@PatchMapping("/api/v1/orgs/{orgId}/events/{eventId}/promoters/{linkId}")
	EventPromotersResponse setActive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId, @PathVariable UUID linkId, @Valid @RequestBody SetActiveRequest body) {
		return response(promoters.setActive(orgId, eventId, linkId, user.id(), body.active()));
	}

	private EventPromotersResponse response(EventPromoters result) {
		String eventUrl = webBaseUrl + "/e/" + result.event().slug();
		return new EventPromotersResponse(
				result.links().stream().map(view -> link(view, eventUrl, result.canManage())).toList(),
				result.total().tickets(), result.total().revenueCents(), result.canManage());
	}

	/** Telefone só para quem gerencia: um promoter não vê o contato dos outros. */
	private static LinkResponse link(LinkView view, String eventUrl, boolean canManage) {
		Promoter p = view.promoter();
		return new LinkResponse(view.link().getId(), p.getId(), p.getName(), canManage ? p.getPhone() : null,
				p.getUserId() != null, view.link().getCode(), eventUrl + "?p=" + view.link().getCode(),
				view.link().isActive(), view.totals().tickets(), view.totals().revenueCents());
	}

}
