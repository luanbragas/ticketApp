package com.festa.shared.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

	@Test
	void hasVersion7AndRfcVariant() {
		UUID id = UuidV7.generate();

		assertThat(id.version()).isEqualTo(7);
		assertThat(id.variant()).isEqualTo(2);
	}

	@Test
	void encodesTimestampInFirst48Bits() {
		long millis = 1_791_331_200_000L;

		UUID id = UuidV7.generate(millis);

		assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(millis);
	}

	@Test
	void sortsByCreationTime() {
		UUID earlier = UuidV7.generate(1_000L);
		UUID later = UuidV7.generate(2_000L);

		assertThat(earlier.toString()).isLessThan(later.toString());
	}

	@Test
	void isUnique() {
		Set<UUID> ids = new HashSet<>();
		for (int i = 0; i < 10_000; i++) {
			ids.add(UuidV7.generate());
		}

		assertThat(ids).hasSize(10_000);
	}

}
