package com.festa.promoter.app;

import com.festa.event.api.EventDirectory;
import com.festa.event.api.EventDirectory.EventRef;
import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.promoter.domain.Promoter;
import com.festa.promoter.domain.PromoterLink;
import com.festa.promoter.infra.PromoterLinkRepository;
import com.festa.promoter.infra.PromoterRepository;
import com.festa.promoter.infra.PromoterSalesStore;
import com.festa.promoter.infra.PromoterSalesStore.Totals;
import com.festa.shared.text.Slugs;
import com.festa.shared.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Promoters e links por evento (PLAN.md M7, ADR-009). Dono, admin e gerente veem e gerenciam tudo;
 * o promoter vê só o próprio link e as próprias vendas (SECURITY.md).
 */
@Service
public class PromoterService {

	private static final Role[] MANAGERS = { Role.OWNER, Role.ADMIN, Role.MANAGER };
	private static final Role[] VIEWERS = { Role.OWNER, Role.ADMIN, Role.MANAGER, Role.PROMOTER };
	private static final int MAX_CODE_SUFFIX = 50;

	private final PromoterRepository promoters;
	private final PromoterLinkRepository links;
	private final PromoterSalesStore sales;
	private final TenantGuard tenantGuard;
	private final EventDirectory events;

	PromoterService(PromoterRepository promoters, PromoterLinkRepository links, PromoterSalesStore sales,
			TenantGuard tenantGuard, EventDirectory events) {
		this.promoters = promoters;
		this.links = links;
		this.sales = sales;
		this.tenantGuard = tenantGuard;
		this.events = events;
	}

	public record LinkView(PromoterLink link, Promoter promoter, Totals totals) {
	}

	/** {@code canManage}: a tela mostra criar/desativar e o telefone dos promoters. */
	public record EventPromoters(EventRef event, List<LinkView> links, Totals total, boolean canManage) {
	}

	/** Novo promoter (nome, telefone e, se for membro da equipe, o usuário) ou um já cadastrado. */
	public record AddPromoter(UUID promoterId, String name, String phone, UUID userId, String code) {
	}

	@Transactional(readOnly = true)
	public EventPromoters forEvent(UUID organizationId, UUID eventId, UUID userId) {
		Role role = tenantGuard.requireRole(organizationId, userId, VIEWERS);
		EventRef event = event(organizationId, eventId);
		boolean canManage = role != Role.PROMOTER;
		Map<UUID, Promoter> byId = promoters.findByOrganizationIdOrderByName(organizationId).stream()
			.collect(Collectors.toMap(Promoter::getId, Function.identity()));
		Map<UUID, Totals> totals = sales.byPromoter(eventId, organizationId);
		List<LinkView> views = links.findByEventIdAndOrganizationIdOrderByCreatedAt(eventId, organizationId).stream()
			.map(link -> new LinkView(link, byId.get(link.getPromoterId()),
					totals.getOrDefault(link.getPromoterId(), Totals.ZERO)))
			.filter(view -> canManage || userId.equals(view.promoter().getUserId()))
			.toList();
		Totals total = new Totals(views.stream().mapToInt(v -> v.totals().tickets()).sum(),
				views.stream().mapToLong(v -> v.totals().revenueCents()).sum());
		return new EventPromoters(event, views, total, canManage);
	}

	@Transactional(readOnly = true)
	public List<Promoter> list(UUID organizationId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, MANAGERS);
		return promoters.findByOrganizationIdOrderByName(organizationId);
	}

	@Transactional
	public EventPromoters addToEvent(UUID organizationId, UUID eventId, UUID userId, AddPromoter request) {
		tenantGuard.requireRole(organizationId, userId, MANAGERS);
		EventRef event = event(organizationId, eventId);
		if (!event.editable()) {
			throw new ApiException(HttpStatus.CONFLICT, "invalid-event-status", "Estado do evento não permite",
				"Evento encerrado ou cancelado não ganha promoter novo.");
		}
		Promoter promoter = request.promoterId() != null ? existing(organizationId, request.promoterId())
				: newPromoter(organizationId, request);
		if (links.existsByEventIdAndPromoterId(eventId, promoter.getId())) {
			throw new ApiException(HttpStatus.CONFLICT, "promoter-already-linked", "Promoter já está no evento",
				promoter.getName() + " já tem link nesta festa.");
		}
		String code = request.code() != null && !request.code().isBlank() ? requestedCode(eventId, request.code())
				: availableCode(eventId, promoter.getName());
		try {
			links.saveAndFlush(new PromoterLink(promoter, eventId, code));
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "promoter-code-taken", "Código em uso",
				"Outro link pegou esse código agora. Tente de novo.");
		}
		return forEvent(organizationId, eventId, userId);
	}

	/** Desativar para de atribuir compras novas; o que já foi vendido continua contando. */
	@Transactional
	public EventPromoters setActive(UUID organizationId, UUID eventId, UUID linkId, UUID userId, boolean active) {
		tenantGuard.requireRole(organizationId, userId, MANAGERS);
		links.findByIdAndEventIdAndOrganizationId(linkId, eventId, organizationId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Link não encontrado."))
			.setActive(active);
		return forEvent(organizationId, eventId, userId);
	}

	private Promoter newPromoter(UUID organizationId, AddPromoter request) {
		if (request.name() == null || request.name().isBlank()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "promoter-name-required", "Falta o nome",
				"Informe o nome do promoter.");
		}
		if (request.userId() == null) {
			return promoters.save(new Promoter(organizationId, null, request.name(), request.phone()));
		}
		// Membro da equipe: precisa ter papel de promoter, e cada membro é um promoter só.
		try {
			tenantGuard.requireRole(organizationId, request.userId(), Role.PROMOTER);
		}
		catch (ApiException ex) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "user-not-promoter", "Membro sem papel de promoter",
				"Essa pessoa precisa estar na equipe com o papel Promoter.");
		}
		return promoters.findByOrganizationIdAndUserId(organizationId, request.userId())
			.orElseGet(() -> promoters.save(new Promoter(organizationId, request.userId(), request.name(),
					request.phone())));
	}

	private Promoter existing(UUID organizationId, UUID promoterId) {
		return promoters.findByIdAndOrganizationId(promoterId, organizationId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Promoter não encontrado."));
	}

	private String requestedCode(UUID eventId, String code) {
		String normalized = code.trim().toLowerCase();
		if (!PromoterLink.isValidCode(normalized)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "invalid-promoter-code", "Código inválido",
				"Use de 3 a 30 letras minúsculas, números e hífens.");
		}
		if (links.existsByEventIdAndCode(eventId, normalized)) {
			throw new ApiException(HttpStatus.CONFLICT, "promoter-code-taken", "Código em uso",
				"Já existe um link com esse código nesta festa.");
		}
		return normalized;
	}

	private String availableCode(UUID eventId, String name) {
		String base = Slugs.fromName(name, PromoterLink.CODE_MAX_LENGTH);
		if (base.length() < PromoterLink.CODE_MIN_LENGTH) {
			base = "promo" + (base.isEmpty() ? "" : "-" + base);
		}
		if (!links.existsByEventIdAndCode(eventId, base)) {
			return base;
		}
		for (int suffix = 2; suffix <= MAX_CODE_SUFFIX; suffix++) {
			String code = Slugs.withSuffix(base, suffix, PromoterLink.CODE_MAX_LENGTH);
			if (!links.existsByEventIdAndCode(eventId, code)) {
				return code;
			}
		}
		throw new ApiException(HttpStatus.CONFLICT, "promoter-code-taken", "Código em uso",
			"Escolha um código para este promoter.");
	}

	private EventRef event(UUID organizationId, UUID eventId) {
		return events.find(organizationId, eventId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Evento não encontrado."));
	}

}
