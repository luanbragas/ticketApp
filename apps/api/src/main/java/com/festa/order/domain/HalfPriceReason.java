package com.festa.order.domain;

/**
 * Benefício de meia-entrada declarado pelo titular (Lei 12.933/2013 e Estatuto da Pessoa Idosa). O
 * documento é conferido na entrada; leis estaduais e municipais extras ficam para quando houver piloto.
 */
public enum HalfPriceReason {

	/** Estudante com carteira (CIE). */
	STUDENT,
	/** Pessoa com deficiência (e acompanhante, quando necessário). */
	PCD,
	/** Jovem de 15 a 29 anos de baixa renda (ID Jovem). */
	YOUTH_LOW_INCOME,
	/** Pessoa com 60 anos ou mais. */
	SENIOR

}
