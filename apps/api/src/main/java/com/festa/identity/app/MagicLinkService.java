package com.festa.identity.app;

import com.festa.shared.security.SecureToken;
import com.festa.identity.domain.User;
import com.festa.identity.infra.MagicLinkRepository;
import com.festa.identity.infra.UserRepository;
import com.festa.notification.api.EmailMessage;
import com.festa.notification.api.EmailSender;
import com.festa.shared.id.UuidV7;
import com.festa.shared.security.SessionRevoker;
import com.festa.shared.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Link mágico de acesso (SECURITY.md §Autenticação, ADR-004). */
@Service
public class MagicLinkService {

	static final Duration VALIDITY = Duration.ofMinutes(15);
	static final Duration RATE_WINDOW = Duration.ofHours(1);
	static final int MAX_LINKS_PER_WINDOW = 5;

	private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);

	private final MagicLinkRepository links;
	private final UserRepository users;
	private final EmailSender emailSender;
	private final SessionRevoker sessionRevoker;
	private final Clock clock;
	private final String webBaseUrl;

	MagicLinkService(MagicLinkRepository links, UserRepository users, EmailSender emailSender,
			SessionRevoker sessionRevoker, Clock clock,
			@Value("${festa.web.base-url}") String webBaseUrl) {
		this.links = links;
		this.users = users;
		this.emailSender = emailSender;
		this.sessionRevoker = sessionRevoker;
		this.clock = clock;
		this.webBaseUrl = webBaseUrl;
	}

	/**
	 * Envia um link para o e-mail, exista conta ou não. Acima do limite por e-mail, não envia
	 * e não avisa quem pediu, para não servir de ferramenta de spam nem revelar nada.
	 */
	public void request(String email) {
		String normalizedEmail = User.normalizeEmail(email);
		Instant now = clock.instant();
		if (links.countCreatedSince(normalizedEmail, now.minus(RATE_WINDOW)) >= MAX_LINKS_PER_WINDOW) {
			log.info("Limite de links mágicos atingido; pedido ignorado");
			return;
		}
		String token = SecureToken.generate();
		links.insert(UuidV7.generate(), normalizedEmail, SecureToken.hash(token), now.plus(VALIDITY), now);
		emailSender.send(new EmailMessage(normalizedEmail, "Seu link de acesso ao FESTA", """
				Olá!

				Use o link abaixo para entrar no FESTA e ver seus ingressos:

				%s/link-magico#token=%s

				O link vale por %d minutos e só pode ser usado uma vez.
				Se você não pediu este acesso, ignore este e-mail.
				""".formatted(webBaseUrl, token, VALIDITY.toMinutes())));
	}

	/** Consome o link e devolve o usuário dono do e-mail, criando a conta no primeiro acesso. */
	@Transactional
	public User consume(String token) {
		Instant now = clock.instant();
		String email = links.consume(SecureToken.hash(token), now)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid-magic-link",
				"Link inválido ou expirado", "Este link já foi usado ou expirou. Peça um novo link de acesso."));
		User user = users.findByEmail(email)
			.orElseGet(() -> users.save(User.registerFromVerifiedEmail(email, now)));
		if (user.confirmEmailOwnership(now)) {
			// Senha definida por quem não provou ser dono do e-mail: derruba as sessões que ela abriu.
			sessionRevoker.revokeAll(user.getId());
		}
		return user;
	}

}
