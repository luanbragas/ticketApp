package com.festa.shared.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalDataCipherTest {

	private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
	private static final String OTHER = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

	private final PersonalDataCipher cipher = new PersonalDataCipher(KEY, OTHER);

	@Test
	void encryptsWithRandomIvAndDecryptsBack() {
		byte[] a = cipher.encrypt("52998224725");
		byte[] b = cipher.encrypt("52998224725");

		assertThat(a).isNotEqualTo(b);
		assertThat(new String(a, StandardCharsets.ISO_8859_1)).doesNotContain("52998224725");
		assertThat(cipher.decrypt(a)).isEqualTo("52998224725");
	}

	@Test
	void hashIsStableAndDependsOnTheKey() {
		assertThat(cipher.hash("52998224725")).isEqualTo(cipher.hash("52998224725"));
		assertThat(new PersonalDataCipher(KEY, KEY).hash("52998224725")).isNotEqualTo(cipher.hash("52998224725"));
	}

	@Test
	void tamperedDataIsRejected() {
		byte[] sealed = cipher.encrypt("52998224725");
		sealed[sealed.length - 1] ^= 1;

		assertThatThrownBy(() -> cipher.decrypt(sealed)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void refusesShortKeys() {
		assertThatThrownBy(() -> new PersonalDataCipher("c2hvcnQ=", OTHER))
			.hasMessageContaining("32 bytes");
	}

}
