package com.festa.compliance.api;

import java.util.Map;
import java.util.UUID;

/**
 * Registro de ações sensíveis (SECURITY.md §Auditoria): desfazer check-in, reembolso, preço de lote, papel
 * de membro... Grava na transação de quem chama: se a ação desfizer, o registro some junto.
 */
public interface AuditLog {

	/**
	 * @param action verbo no passado, em inglês (ex.: {@code checkin.undone})
	 * @param data detalhes sem dado pessoal sensível (nada de CPF ou e-mail completo)
	 */
	void record(UUID organizationId, UUID actorUserId, String action, String entity, UUID entityId,
			Map<String, Object> data);

}
