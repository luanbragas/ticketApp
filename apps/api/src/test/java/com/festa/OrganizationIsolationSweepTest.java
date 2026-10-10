package com.festa;

import com.festa.TestShop.Shop;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SECURITY.md checklist "Teste de isolamento entre organizações em todos os endpoints do painel": percorre
 * TODAS as rotas {@code /api/v1/orgs/{orgId}/...} registradas e chama cada uma com a sessão de outra
 * organização apontando para os ids da vítima. Nenhuma pode responder 2xx. Rota nova entra sozinha.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrganizationIsolationSweepTest {

	/** Respostas aceitáveis: não existe (404), sem papel (403), corpo inválido (400), método (405). */
	private static final Set<Integer> REFUSED = Set.of(400, 403, 404, 405);

	@Autowired
	MockMvc mvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping mappings;

	@Test
	void noPanelRouteAnswersAnotherOrganization() throws Exception {
		Shop victim = TestShop.open(mvc, 100, null);
		Shop intruder = TestShop.open(mvc, 100, null);

		List<String> leaks = new ArrayList<>();
		int checked = 0;
		for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
			for (String pattern : info.getPatternValues()) {
				if (!pattern.startsWith("/api/v1/orgs/{orgId}")) {
					continue;
				}
				Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
				for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
					String url = pattern.replace("{orgId}", victim.org())
						.replace("{eventId}", victim.eventId())
						.replace("{typeId}", UUID.randomUUID().toString())
						.replace("{batchId}", victim.pista())
						.replace("{linkId}", UUID.randomUUID().toString())
						.replace("{checkinId}", UUID.randomUUID().toString())
						.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
					int status = intruder.producer()
						.send(MockMvcRequestBuilders.request(HttpMethod.valueOf(method.name()), url), "{}")
						.andReturn().getResponse().getStatus();
					checked++;
					if (!REFUSED.contains(status)) {
						leaks.add(method + " " + pattern + " -> " + status);
					}
				}
			}
		}

		assertThat(checked).as("rotas do painel percorridas").isGreaterThan(20);
		assertThat(leaks).as("rotas que responderam para outra organização").isEmpty();
	}

}
