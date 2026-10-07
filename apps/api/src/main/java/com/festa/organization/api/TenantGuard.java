package com.festa.organization.api;

import java.util.UUID;

/**
 * Checagem central de acesso ao painel (SECURITY.md §Autorização). Todo caso de uso de dado de
 * organização chama isto com o {@code organizationId} do recurso (nunca do corpo da requisição).
 */
public interface TenantGuard {

	/**
	 * Garante que o usuário é membro da organização com um dos papéis permitidos.
	 * Sem nenhum papel informado, qualquer membro passa.
	 *
	 * @return o papel do usuário na organização
	 * @throws com.festa.shared.web.ApiException 404 se não for membro (não revela que a organização existe),
	 *         403 se for membro sem papel suficiente
	 */
	Role requireRole(UUID organizationId, UUID userId, Role... allowed);

}
