package com.festa.compliance.web;

import com.festa.compliance.api.Consents;
import com.festa.compliance.api.Consents.Version;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** Versões vigentes dos termos e da política de privacidade, para a web mostrar e linkar. */
@RestController
class LegalController {

	private final Consents consents;

	LegalController(Consents consents) {
		this.consents = consents;
	}

	@GetMapping("/api/v1/public/legal")
	ResponseEntity<List<Version>> current() {
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
			.body(consents.current());
	}

}
