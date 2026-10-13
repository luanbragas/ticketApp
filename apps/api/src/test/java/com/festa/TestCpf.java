package com.festa;

import java.util.concurrent.ThreadLocalRandom;

/** CPFs válidos (dígitos verificadores certos) e diferentes a cada chamada, para testes. */
public final class TestCpf {

	private TestCpf() {
	}

	public static String random() {
		int[] d = new int[11];
		ThreadLocalRandom random = ThreadLocalRandom.current();
		do {
			for (int i = 0; i < 9; i++) {
				d[i] = random.nextInt(10);
			}
		}
		while (d[0] == d[1] && d[1] == d[2]);
		d[9] = check(d, 9);
		d[10] = check(d, 10);
		StringBuilder cpf = new StringBuilder();
		for (int digit : d) {
			cpf.append(digit);
		}
		return cpf.toString();
	}

	private static int check(int[] d, int length) {
		int sum = 0;
		for (int i = 0; i < length; i++) {
			sum += d[i] * (length + 1 - i);
		}
		int rest = (sum * 10) % 11;
		return rest == 10 ? 0 : rest;
	}

}
