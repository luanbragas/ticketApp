package com.festa;

import org.springframework.boot.SpringApplication;

/** Sobe a API localmente com Postgres via Testcontainers: {@code ./mvnw spring-boot:test-run}. */
public class TestFestaApplication {

	public static void main(String[] args) {
		SpringApplication.from(FestaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
