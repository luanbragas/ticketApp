package com.festa.order.api;

import java.util.List;
import java.util.UUID;

/**
 * Evento de domínio publicado no outbox quando o pagamento de um pedido é confirmado (ARCHITECTURE.md
 * §Pagamento, passo 4d). Leva tudo o que a emissão de ingressos precisa, para o módulo ticketing não ler
 * as tabelas de pedido. CPF vai só cifrado.
 */
public record OrderPaid(UUID orderId, UUID eventId, UUID organizationId, String buyerName, String buyerEmail,
		List<Item> items) {

	public static final String TYPE = "OrderPaid";

	/** @param holderCpfEncrypted CPF do titular cifrado (AES-GCM, ver PersonalDataCipher), em base64 */
	public record Item(UUID orderItemId, UUID batchId, String holderName, String holderCpfEncrypted,
			String holderCpfHash, boolean halfPrice) {
	}

}
