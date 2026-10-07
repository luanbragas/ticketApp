package com.festa.organization.web;

import com.festa.organization.api.Role;
import com.festa.organization.app.InvitationService;
import com.festa.organization.app.InvitationService.InvitationPreview;
import com.festa.organization.web.OrganizationController.OrganizationResponse;
import com.festa.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Tela de aceite do convite: ver os dados (público) e aceitar (logado com o e-mail convidado). */
@RestController
class InvitationController {

	private final InvitationService invitations;

	InvitationController(InvitationService invitations) {
		this.invitations = invitations;
	}

	/** Token vai no corpo, não na URL, para não aparecer em logs. */
	record TokenRequest(@NotBlank(message = "Convite inválido.") @Size(max = 100, message = "Convite inválido.") String token) {
	}

	record PreviewResponse(String organizationName, String email, Role role, String roleLabel) {

		static PreviewResponse of(InvitationPreview preview) {
			return new PreviewResponse(preview.organizationName(), preview.email(), preview.role(),
				preview.role().label());
		}

	}

	@PostMapping("/api/v1/public/invitations/preview")
	PreviewResponse preview(@Valid @RequestBody TokenRequest body) {
		return PreviewResponse.of(invitations.preview(body.token()));
	}

	@PostMapping("/api/v1/invitations/accept")
	OrganizationResponse accept(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody TokenRequest body) {
		return OrganizationResponse.of(invitations.accept(body.token(), user.id()));
	}

}
