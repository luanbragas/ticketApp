package com.festa.shared.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Encerra no servidor todas as sessões de um usuário (o nome do principal é o id; ver AuthenticatedUser). */
@Component
public class SessionRevoker {

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
		this.sessions = sessions;
	}

	public void revokeAll(UUID userId) {
		sessions.findByPrincipalName(userId.toString()).keySet().forEach(sessions::deleteById);
	}

}
