package com.festa.organization.web;

import com.festa.organization.api.Role;
import com.festa.organization.app.InvitationService;
import com.festa.organization.app.InvitationService.Member;
import com.festa.organization.app.InvitationService.Team;
import com.festa.organization.domain.Invitation;
import com.festa.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Equipe da organização: listar membros e convidar por e-mail. */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/members")
class MemberController {

	private final InvitationService invitations;

	MemberController(InvitationService invitations) {
		this.invitations = invitations;
	}

	record InviteRequest(
			@NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.")
			@Size(max = 320, message = "E-mail muito longo.") String email,
			@NotNull(message = "Escolha um papel.") Role role) {
	}

	record InvitationResponse(UUID id, String email, Role role, Instant expiresAt) {

		static InvitationResponse of(Invitation invitation) {
			return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getRole(),
				invitation.getExpiresAt());
		}

	}

	record TeamResponse(List<Member> members, List<InvitationResponse> pendingInvitations) {

		static TeamResponse of(Team team) {
			return new TeamResponse(team.members(),
				team.pendingInvitations().stream().map(InvitationResponse::of).toList());
		}

	}

	@GetMapping
	TeamResponse list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId) {
		return TeamResponse.of(invitations.team(orgId, user.id()));
	}

	@PostMapping
	ResponseEntity<InvitationResponse> invite(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID orgId, @Valid @RequestBody InviteRequest body) {
		Invitation invitation = invitations.invite(orgId, user.id(), body.email(), body.role());
		return ResponseEntity.status(HttpStatus.CREATED).body(InvitationResponse.of(invitation));
	}

}
