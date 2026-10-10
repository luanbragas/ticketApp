package com.festa.shared.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Rate limit por IP nas rotas que atraem abuso (SECURITY.md §Abuso): login, cadastro, link mágico,
 * criação de pedido, cotação e webhook. Em memória (Bucket4j): com mais de uma instância, cada uma conta
 * a sua parte, o que basta no MVP. Atrás de proxy, o IP vem de {@code server.forward-headers-strategy}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

	/** Limite de uma rota: {@code capacity} pedidos por {@code period}, por IP. */
	record Rule(String method, String pattern, int capacity, Duration period) {
	}

	/**
	 * Generoso em pedido e cotação: numa calourada, muita gente compra pelo mesmo Wi-Fi do campus (mesmo IP).
	 */
	static final List<Rule> RULES = List.of(
			new Rule("POST", "/api/v1/auth/login", 10, Duration.ofMinutes(1)),
			new Rule("POST", "/api/v1/auth/signup", 5, Duration.ofMinutes(1)),
			new Rule("POST", "/api/v1/public/magic-links", 5, Duration.ofMinutes(1)),
			new Rule("POST", "/api/v1/auth/magic-link/consume", 10, Duration.ofMinutes(1)),
			new Rule("POST", "/api/v1/public/orders", 60, Duration.ofMinutes(1)),
			new Rule("POST", "/api/v1/public/events/*/quote", 120, Duration.ofMinutes(1)),
			new Rule("POST", "/api/v1/webhooks/**", 600, Duration.ofMinutes(1)));

	/** Teto de IPs lembrados: o mais antigo sai (memória limitada mesmo sob ataque de muitos IPs). */
	private static final int MAX_KEYS = 100_000;

	private final AntPathMatcher paths = new AntPathMatcher();
	private final boolean enabled;
	private final Map<String, Bucket> buckets = Collections.synchronizedMap(new LinkedHashMap<>(1024, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
			return size() > MAX_KEYS;
		}
	});

	RateLimitFilter(@Value("${festa.rate-limit.enabled:true}") boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !enabled || rule(request) == null;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Rule rule = rule(request);
		String key = rule.method() + " " + rule.pattern() + " " + request.getRemoteAddr();
		Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
			.addLimit(Bandwidth.builder().capacity(rule.capacity()).refillGreedy(rule.capacity(), rule.period()).build())
			.build());
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			chain.doFilter(request, response);
			return;
		}
		long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader("Retry-After", String.valueOf(retryAfter));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("""
				{"type":"https://festa.com/errors/rate-limited","title":"Muitas tentativas","status":429,\
				"detail":"Muitas tentativas seguidas. Espere %d segundos e tente de novo."}""".formatted(retryAfter));
	}

	private Rule rule(HttpServletRequest request) {
		String path = request.getRequestURI();
		for (Rule rule : RULES) {
			if (rule.method().equals(request.getMethod()) && paths.match(rule.pattern(), path)) {
				return rule;
			}
		}
		return null;
	}

}
