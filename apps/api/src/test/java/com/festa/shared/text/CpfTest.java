package com.festa.shared.text;

import com.festa.TestCpf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpfTest {

	@Test
	void validatesCheckDigits() {
		assertThat(Cpf.isValid("529.982.247-25")).isTrue();
		assertThat(Cpf.isValid("52998224725")).isTrue();
		assertThat(Cpf.isValid("529.982.247-26")).isFalse();
		assertThat(Cpf.isValid("111.111.111-11")).isFalse();
		assertThat(Cpf.isValid("1234")).isFalse();
		assertThat(Cpf.isValid(null)).isFalse();
		assertThat(Cpf.isValid(TestCpf.random())).isTrue();
	}

	@Test
	void masksAllButTheMiddle() {
		assertThat(Cpf.mask("529.982.247-25")).isEqualTo("***.982.247-**");
		assertThat(Cpf.mask("x")).isEqualTo("***.***.***-**");
	}

}
