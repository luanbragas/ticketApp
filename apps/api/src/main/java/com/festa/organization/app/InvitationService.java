package com.festa.organization.app;

import com.festa.identity.api.UserDirectory;
import com.festa.identity.api.UserDirectory.UserSummary;
import com.festa.notification.api.EmailMessage;
import com.festa.notification.api.EmailSender;
import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.organization.domain.Invitation;
import com.festa.organization.domain.InvitationPolicy;
import com.festa.organization.domain.Organization;
import com.festa.organization.domain.OrganizationMember;
import com.festa.organization.infra.InvitationRepository;
import com.festa.organization.infra.OrganizationMemberRepository;
import com.festa.organization.infra.OrganizationRepository;
import com.festa.shared.security.SecureToken;
import com.festa.shared.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Convite de membros (regras em {@link InvitationPolicy}). */
@Service
public class InvitationService {

	private final InvitationRepository invitations;
	private final OrganizationRepository organizations;
	private final OrganizationMemberRepository members;
	private final TenantGuard tenantGuard;
	private final UserDirectory userDirectory;
	private final EmailSender emailSender;
	private final Clock clock;
	private final String webBaseUrl;

	InvitationService(InvitationRepository invitations, OrganizationRepository organizations,
			OrganizationMemberRepository members, TenantGuard tenantGuard, UserDirectory userDirectory,
			EmailSender emailSender, Clock clock, @Value("${festa.web.base-url}") String webBaseUrl) {
		this.invitations = invitations;
		this.organizations = organizations;
		this.members = members;
		this.tenantGuard = tenantGuard;
		this.userDirectory = userDirectory;
		this.emailSender = emailSender;
		this.clock = clock;
		this.webBaseUrl = webBaseUrl;
	}

	public record Member(UUID userId, String name, String email, Role role) {
	}

	public record Team(List<Member> members, List<Invitation> pendingInvitations) {
	}

	public record InvitationPreview(String organizationName, String email, Role role) {
	}

	@Transactional
	public Invitation invite(UUID organizationId, UUID inviterId, String email, Role role) {
		Role inviterRole = tenantGuard.requireRole(organizationId, inviterId);
		if (!InvitationPolicy.canInvite(inviterRole, role)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "role-not-allowed", "Papel não permitido",
				InvitationPolicy.canInvite(inviterRole)
					? "Seu papel não permite convidar alguém como " + role.label() + "."
					: "Seu papel não permite convidar membros.");
		}
		boolean alreadyMember = userDirectory.findByEmail(email)
			.map(user -> members.existsByOrganizationIdAndUserId(organizationId, user.id()))
			.orElse(false);
		if (alreadyMember) {
			throw alreadyMember();
		}

		String token = SecureToken.generate();
		Invitation invitation = invitations.save(new Invitation(organizationId, email, role, SecureToken.hash(token),
			inviterId, clock.instant()));

		Organization organization = organizations.getReferenceById(organizationId);
		String inviterName = userDirectory.find(inviterId).map(UserSummary::name).orElse(null);
		emailSender.send(new EmailMessage(invitation.getEmail(), "Convite para " + organization.getName() + " no FESTA",
			"""
				Olá!

				%s convidou você para fazer parte de %s no FESTA como %s.

				Para aceitar, abra o link:

				%s/convite#token=%s

				O convite vale por %d dias. Se você não esperava este convite, ignore este e-mail.
				""".formatted(inviterName != null ? inviterName : "Alguém", organization.getName(), role.label(),
				webBaseUrl, token, Invitation.VALIDITY.toDays())));
		return invitation;
	}

	/** Dados para a tela de aceite. Público: quem tem o token já recebeu o e-mail. */
	@Transactional(readOnly = true)
	public InvitationPreview preview(String token) {
		Invitation invitation = invitations.findByTokenHash(SecureToken.hash(token))
			.filter(found -> found.isPending(clock.instant()))
			.orElseThrow(InvitationService::invalidInvitation);
		Organization organization = organizations.getReferenceById(invitation.getOrganizationId());
		return new InvitationPreview(organization.getName(), invitation.getEmail(), invitation.getRole());
	}

	/** Aceita o convite. Só vale para o usuário logado com o mesmo e-mail convidado. */
	@Transactional
	public OrganizationService.Membership accept(String token, UUID userId) {
		Instant now = clock.instant();
		Invitation invitation = invitations.findByTokenHashForUpdate(SecureToken.hash(token))
			.orElseThrow(InvitationService::invalidInvitation);
		String userEmail = userDirectory.find(userId).map(UserSummary::email).orElseThrow(InvitationService::invalidInvitation);
		if (!invitation.canBeAcceptedBy(userEmail, now)) {
			throw invalidInvitation();
		}
		if (members.existsByOrganizationIdAndUserId(invitation.getOrganizationId(), userId)) {
			throw alreadyMember();
		}
		invitation.accept(userId, userEmail, now);
		members.save(new OrganizationMember(invitation.getOrganizationId(), userId, invitation.getRole()));
		return new OrganizationService.Membership(organizations.findById(invitation.getOrganizationId()).orElseThrow(),
			invitation.getRole());
	}

	/** Membros e convites pendentes. Promoter e operador de check-in não veem a equipe (minimização, LGPD). */
	@Transactional(readOnly = true)
	public Team team(UUID organizationId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, Role.OWNER, Role.ADMIN, Role.MANAGER);
		List<OrganizationMember> memberships = members.findByOrganizationIdOrderByCreatedAt(organizationId);
		Map<UUID, UserSummary> users = userDirectory.findAll(memberships.stream().map(OrganizationMember::getUserId).toList());
		List<Member> team = memberships.stream()
			.map(member -> {
				UserSummary user = users.get(member.getUserId());
				return new Member(member.getUserId(), user.name(), user.email(), member.getRole());
			})
			.toList();
		return new Team(team, invitations.findPending(organizationId, clock.instant()));
	}

	private static ApiException invalidInvitation() {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "invalid-invitation", "Convite inválido",
			"Este convite não vale para sua conta: ele pode ter expirado, já ter sido usado ou ter sido enviado para outro e-mail.");
	}

	private static ApiException alreadyMember() {
		return new ApiException(HttpStatus.CONFLICT, "already-member", "Já é membro",
			"Esta pessoa já faz parte da organização.");
	}

}
