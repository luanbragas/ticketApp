package com.festa.payment.app;

import com.festa.compliance.api.AuditLog;
import com.festa.event.api.PublishPrerequisite;
import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.payment.domain.PaymentAccount;
import com.festa.payment.infra.OAuthStateStore;
import com.festa.payment.infra.PaymentAccountRepository;
import com.festa.shared.crypto.PersonalDataCipher;
import com.festa.shared.security.SecureToken;
import com.festa.shared.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Conta de recebimento da organização (ADR-003): o produtor autoriza a plataforma no gateway por OAuth e os
 * pagamentos saem em nome dele. Só dono e admin conectam ou desconectam (SECURITY.md).
 */
@Service
public class PaymentAccountService implements PublishPrerequisite {

	private static final Role[] OWNERS = { Role.OWNER, Role.ADMIN };
	private static final Duration STATE_TTL = Duration.ofMinutes(10);
	/** Renova o token do vendedor quando faltar menos que isso para vencer. */
	private static final Duration REFRESH_BEFORE = Duration.ofDays(7);

	private final PaymentAccountRepository accounts;
	private final OAuthStateStore states;
	private final PaymentGateway gateway;
	private final PersonalDataCipher cipher;
	private final TenantGuard tenantGuard;
	private final AuditLog audit;
	private final Clock clock;
	private final String redirectUri;

	PaymentAccountService(PaymentAccountRepository accounts, OAuthStateStore states, PaymentGateway gateway,
			PersonalDataCipher cipher, TenantGuard tenantGuard, AuditLog audit, Clock clock,
			@Value("${festa.web.base-url}") String webBaseUrl) {
		this.accounts = accounts;
		this.states = states;
		this.gateway = gateway;
		this.cipher = cipher;
		this.tenantGuard = tenantGuard;
		this.audit = audit;
		this.clock = clock;
		this.redirectUri = webBaseUrl + "/painel/organizacao/recebimentos";
	}

	public record Status(boolean connected, String provider, String providerUserId, Instant connectedAt) {
	}

	@Transactional(readOnly = true)
	public Status status(UUID organizationId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, Role.OWNER, Role.ADMIN, Role.MANAGER);
		return accounts.findByOrganizationId(organizationId)
			.map(a -> new Status(true, a.getProvider().name(), a.getProviderUserId(), a.getCreatedAt()))
			.orElse(new Status(false, gateway.provider().name(), null, null));
	}

	/** Começa o OAuth: devolve o endereço de autorização do gateway com um state de uso único. */
	@Transactional
	public String connect(UUID organizationId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, OWNERS);
		String state = SecureToken.generate();
		states.save(SecureToken.hash(state), organizationId, userId, clock.instant().plus(STATE_TTL));
		return gateway.authorizationUrl(state, redirectUri);
	}

	/** Volta do OAuth: confere o state, troca o código pela credencial e guarda cifrado. */
	@Transactional
	public Status callback(UUID organizationId, UUID userId, String code, String state) {
		tenantGuard.requireRole(organizationId, userId, OWNERS);
		if (state == null || !states.consume(SecureToken.hash(state), organizationId, userId, clock.instant())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-oauth-state", "Autorização inválida",
				"A autorização expirou ou não começou aqui. Tente conectar de novo.");
		}
		PaymentGateway.Tokens tokens;
		try {
			tokens = gateway.exchangeCode(code, redirectUri);
		}
		catch (RuntimeException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "oauth-exchange-failed", "Não deu para conectar",
				"O Mercado Pago não confirmou a autorização. Tente de novo.");
		}
		PaymentAccount account = accounts.findByOrganizationId(organizationId)
			.orElseGet(() -> new PaymentAccount(organizationId, gateway.provider(), userId));
		store(account, tokens);
		accounts.save(account);
		audit.record(organizationId, userId, "payment-account.connected", "payment_account", account.getId(),
				Map.of("provider", gateway.provider().name(), "providerUserId", tokens.userId()));
		return status(organizationId, userId);
	}

	@Transactional
	public void disconnect(UUID organizationId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, OWNERS);
		accounts.findByOrganizationId(organizationId).ifPresent(account -> {
			accounts.delete(account);
			audit.record(organizationId, userId, "payment-account.disconnected", "payment_account", account.getId(),
					Map.of("provider", account.getProvider().name()));
		});
	}

	/** Credencial do vendedor para cobrar e consultar; renova antes de vencer. */
	@Transactional
	public Optional<PaymentGateway.Seller> seller(UUID organizationId) {
		return accounts.findByOrganizationId(organizationId).map(account -> {
			Instant expiresAt = account.getExpiresAt();
			if (expiresAt != null && account.getRefreshTokenEncrypted() != null
					&& expiresAt.isBefore(clock.instant().plus(REFRESH_BEFORE))) {
				store(account, gateway.refresh(cipher.decrypt(account.getRefreshTokenEncrypted())));
			}
			return new PaymentGateway.Seller(cipher.decrypt(account.getAccessTokenEncrypted()));
		});
	}

	/** Publicar exige conta de recebimento conectada (PLAN.md M2/M5): sem ela, a venda não tem para onde ir. */
	@Override
	@Transactional(readOnly = true)
	public List<String> missingFor(UUID organizationId, UUID eventId) {
		return accounts.existsByOrganizationId(organizationId) ? List.of() : List.of("paymentAccount");
	}

	private void store(PaymentAccount account, PaymentGateway.Tokens tokens) {
		account.store(tokens.userId(), tokens.publicKey(), cipher.encrypt(tokens.accessToken()),
				tokens.refreshToken() == null ? null : cipher.encrypt(tokens.refreshToken()), tokens.expiresAt());
	}

}
