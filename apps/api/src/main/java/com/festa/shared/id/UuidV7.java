package com.festa.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

/** Gera UUIDv7 (RFC 9562): ordenável por tempo, usado como PK de todas as tabelas. */
public final class UuidV7 {

	private static final SecureRandom RANDOM = new SecureRandom();

	private UuidV7() {
	}

	public static UUID generate() {
		return generate(System.currentTimeMillis());
	}

	static UUID generate(long epochMillis) {
		byte[] random = new byte[10];
		RANDOM.nextBytes(random);

		long msb = (epochMillis & 0xFFFF_FFFF_FFFFL) << 16
			| 0x7000L
			| (random[0] & 0x0FL) << 8
			| random[1] & 0xFFL;

		long lsb = 0;
		for (int i = 2; i < 10; i++) {
			lsb = lsb << 8 | random[i] & 0xFFL;
		}
		lsb = lsb & 0x3FFF_FFFF_FFFF_FFFFL | 0x8000_0000_0000_0000L;

		return new UUID(msb, lsb);
	}

}
