package com.festa.promoter.app;

import com.festa.promoter.api.PromoterLinks;
import com.festa.promoter.domain.PromoterLink;
import com.festa.promoter.infra.PromoterLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
class JpaPromoterLinks implements PromoterLinks {

	private final PromoterLinkRepository links;

	JpaPromoterLinks(PromoterLinkRepository links) {
		this.links = links;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UUID> resolve(UUID eventId, String code) {
		if (code == null) {
			return Optional.empty();
		}
		String normalized = code.trim().toLowerCase();
		if (!PromoterLink.isValidCode(normalized)) {
			return Optional.empty();
		}
		return links.findByEventIdAndCodeAndActiveTrue(eventId, normalized).map(PromoterLink::getPromoterId);
	}

}
