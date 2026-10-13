package com.festa.payment.domain;

import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Conta de recebimento da organização no gateway (ADR-003): os pagamentos são criados com o token dela, o
 * dinheiro cai direto para o produtor e a plataforma retém a taxa de serviço. Tokens só cifrados.
 */
@Entity
@Table(name = "payment_accounts")
public class PaymentAccount extends BaseEntity {

	private UUID organizationId;

	@Enumerated(EnumType.STRING)
	private Payment.Provider provider;

	private String providerUserId;

	private String publicKey;

	private byte[] accessTokenEncrypted;

	private byte[] refreshTokenEncrypted;

	private Instant expiresAt;

	private UUID connectedBy;

	protected PaymentAccount() {
	}

	public PaymentAccount(UUID organizationId, Payment.Provider provider, UUID connectedBy) {
		this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
		this.provider = Objects.requireNonNull(provider, "provider");
		this.connectedBy = Objects.requireNonNull(connectedBy, "connectedBy");
	}

	/** Tokens novos (conexão ou renovação), já cifrados por quem chama. */
	public void store(String providerUserId, String publicKey, byte[] accessToken, byte[] refreshToken,
			Instant expiresAt) {
		this.providerUserId = Objects.requireNonNull(providerUserId, "providerUserId");
		this.publicKey = publicKey;
		this.accessTokenEncrypted = Objects.requireNonNull(accessToken, "accessToken");
		this.refreshTokenEncrypted = refreshToken;
		this.expiresAt = expiresAt;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public Payment.Provider getProvider() {
		return provider;
	}

	public String getProviderUserId() {
		return providerUserId;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public byte[] getAccessTokenEncrypted() {
		return accessTokenEncrypted;
	}

	public byte[] getRefreshTokenEncrypted() {
		return refreshTokenEncrypted;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

}
