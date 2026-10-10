package com.festa.checkin.web;

import com.festa.checkin.app.CheckinService;
import com.festa.checkin.app.CheckinService.Participant;
import com.festa.checkin.app.CheckinService.Participants;
import com.festa.checkin.app.CheckinService.Scan;
import com.festa.checkin.app.CheckinService.SyncItem;
import com.festa.checkin.app.CheckinService.SyncOutcome;
import com.festa.shared.security.AuthenticatedUser;
import com.festa.shared.web.ApiException;
import com.festa.ticketing.api.Admission.Outcome;
import com.festa.ticketing.api.Admission.Pass;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Portaria e participantes (BACKEND.md §Check-in, ADR-010). */
@RestController
class CheckinController {

	private static final String DEVICE = "^[A-Za-z0-9_-]{8,64}$";

	private final CheckinService checkins;

	CheckinController(CheckinService checkins) {
		this.checkins = checkins;
	}

	/** Pelo QR ({@code token}) ou pela busca manual ({@code ticketId}). */
	record ScanRequest(@Size(max = 100) String token, UUID ticketId,
			@Pattern(regexp = DEVICE, message = "Aparelho inválido.") String deviceId) {
	}

	record PassResponse(UUID ticketId, String holderName, String typeName, String batchName, boolean halfPrice,
			String status) {

		static PassResponse of(Pass pass) {
			return pass == null ? null : new PassResponse(pass.ticketId(), pass.holderName(), pass.typeName(),
					pass.batchName(), pass.halfPrice(), pass.status());
		}

	}

	record ScanResponse(Outcome outcome, PassResponse ticket, Instant checkedInAt) {
	}

	record ManifestEntryResponse(UUID ticketId, String tokenHash, String holderName, String typeName, boolean halfPrice,
			String status) {
	}

	record ManifestResponse(UUID eventId, String eventName, Instant startsAt, Instant generatedAt,
			List<ManifestEntryResponse> tickets) {
	}

	record SyncItemRequest(@Pattern(regexp = "^[0-9a-f]{64}$") String tokenHash, UUID ticketId,
			@NotNull Instant checkedInAt) {
	}

	record SyncRequest(@NotNull @Pattern(regexp = DEVICE, message = "Aparelho inválido.") String deviceId,
			@NotNull @Size(max = 500, message = "Lote grande demais.") List<@Valid @NotNull SyncItemRequest> items) {
	}

	record SyncItemResponse(String tokenHash, UUID ticketId, Instant checkedInAt, SyncOutcome outcome) {
	}

	record SyncResponse(List<SyncItemResponse> results) {
	}

	record ParticipantResponse(PassResponse ticket, String holderCpf, String buyerEmail, UUID checkinId,
			Instant checkedInAt) {
	}

	record ParticipantsResponse(List<ParticipantResponse> items, int issued, int checkedIn, int duplicates,
			boolean hasMore) {
	}

	@GetMapping("/api/v1/orgs/{orgId}/events/{eventId}/checkin/manifest")
	ResponseEntity<ManifestResponse> manifest(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID orgId, @PathVariable UUID eventId) {
		CheckinService.Manifest manifest = checkins.manifest(orgId, eventId, user.id());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ManifestResponse(eventId,
				manifest.event().name(), manifest.event().startsAt(), manifest.generatedAt(),
				manifest.entries().stream()
					.map(e -> new ManifestEntryResponse(e.ticketId(), e.tokenHash(), e.holderName(), e.typeName(),
							e.halfPrice(), e.status()))
					.toList()));
	}

	@PostMapping("/api/v1/orgs/{orgId}/events/{eventId}/checkins")
	ScanResponse scan(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId, @Valid @RequestBody ScanRequest body) {
		if ((body.token() == null) == (body.ticketId() == null)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "token-or-ticket", "Leitura inválida",
				"Envie o código do QR ou o ingresso da busca.");
		}
		Scan scan = checkins.scan(orgId, eventId, user.id(), body.token(), body.ticketId(), body.deviceId());
		return new ScanResponse(scan.result().outcome(), PassResponse.of(scan.result().pass()), scan.checkedInAt());
	}

	@PostMapping("/api/v1/orgs/{orgId}/events/{eventId}/checkins/sync")
	SyncResponse sync(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId, @Valid @RequestBody SyncRequest body) {
		List<SyncItem> items = body.items().stream().map(item -> {
			if ((item.tokenHash() == null) == (item.ticketId() == null)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "token-or-ticket", "Leitura inválida",
					"Cada leitura precisa do hash do QR ou do ingresso da busca.");
			}
			return new SyncItem(item.tokenHash(), item.ticketId(), item.checkedInAt());
		}).toList();
		return new SyncResponse(checkins.sync(orgId, eventId, user.id(), body.deviceId(), items).stream()
			.map(r -> new SyncItemResponse(r.item().tokenHash(), r.ticketId(), r.item().checkedInAt(), r.outcome()))
			.toList());
	}

	@GetMapping("/api/v1/orgs/{orgId}/events/{eventId}/attendees")
	ParticipantsResponse participants(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId, @RequestParam(required = false) @Size(max = 120) String q,
			@RequestParam(required = false) @Pattern(regexp = "VALID|CHECKED_IN|TRANSFERRED|CANCELLED") String status,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
		Participants result = checkins.participants(orgId, eventId, user.id(), q, status, limit);
		return new ParticipantsResponse(result.items().stream().map(CheckinController::participant).toList(),
				result.counts().issued(), result.counts().checkedIn(), result.duplicates(), result.full());
	}

	@PostMapping("/api/v1/orgs/{orgId}/checkins/{checkinId}/undo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void undo(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID checkinId) {
		checkins.undo(orgId, checkinId, user.id());
	}

	private static ParticipantResponse participant(Participant p) {
		return new ParticipantResponse(PassResponse.of(p.attendee().pass()), p.attendee().holderCpf(),
				p.attendee().buyerEmail(), p.checkin() == null ? null : p.checkin().id(),
				p.checkin() == null ? null : p.checkin().checkedInAt());
	}

}
