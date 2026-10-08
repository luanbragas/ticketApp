package com.festa.shared.text;

/** CPF: só dígitos, dígitos verificadores e máscara de exibição (SECURITY.md: {@code ***.456.789-**}). */
public final class Cpf {

	private Cpf() {
	}

	/** "123.456.789-09" → "12345678909"; null vira vazio. */
	public static String digits(String value) {
		return value == null ? "" : value.replaceAll("\\D", "");
	}

	/** 11 dígitos, não repetidos, com os dois verificadores certos. */
	public static boolean isValid(String value) {
		String d = digits(value);
		if (d.length() != 11 || d.chars().distinct().count() == 1) {
			return false;
		}
		return check(d, 9) == d.charAt(9) - '0' && check(d, 10) == d.charAt(10) - '0';
	}

	/** Mostra só o miolo: "***.456.789-**". */
	public static String mask(String value) {
		String d = digits(value);
		if (d.length() != 11) {
			return "***.***.***-**";
		}
		return "***." + d.substring(3, 6) + "." + d.substring(6, 9) + "-**";
	}

	private static int check(String d, int length) {
		int sum = 0;
		for (int i = 0; i < length; i++) {
			sum += (d.charAt(i) - '0') * (length + 1 - i);
		}
		int rest = (sum * 10) % 11;
		return rest == 10 ? 0 : rest;
	}

}
