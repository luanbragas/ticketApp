package com.festa.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Leitura de dados básicos de usuários para outros módulos (que não podem ler a tabela users). */
public interface UserDirectory {

	Optional<UserSummary> find(UUID userId);

	/** Busca pelo e-mail, normalizado como no cadastro (sem espaços, minúsculas). */
	Optional<UserSummary> findByEmail(String email);

	/** Usuários encontrados, por id. Ids inexistentes ficam de fora. */
	Map<UUID, UserSummary> findAll(Collection<UUID> userIds);

	/** @param name pode ser nulo (conta criada por link mágico, ADR-004) */
	record UserSummary(UUID id, String name, String email) {
	}

}
