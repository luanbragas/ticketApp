package com.festa.identity.app;

import com.festa.identity.api.UserDirectory;
import com.festa.identity.domain.User;
import com.festa.identity.infra.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class UserDirectoryService implements UserDirectory {

	private final UserRepository users;

	UserDirectoryService(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UserSummary> find(UUID userId) {
		return users.findById(userId).map(UserDirectoryService::summary);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UserSummary> findByEmail(String email) {
		return users.findByEmail(User.normalizeEmail(email)).map(UserDirectoryService::summary);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<UUID, UserSummary> findAll(Collection<UUID> userIds) {
		return users.findAllById(userIds).stream()
			.map(UserDirectoryService::summary)
			.collect(Collectors.toMap(UserSummary::id, Function.identity()));
	}

	private static UserSummary summary(User user) {
		return new UserSummary(user.getId(), user.getName(), user.getEmail());
	}

}
