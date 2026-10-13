package com.festa.order.app;

import com.festa.order.domain.FeePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OrderConfig {

	/** Taxa de serviço vem do ambiente (SERVICE_FEE_BPS, SERVICE_FEE_MIN_CENTS): valor definido antes do piloto. */
	@Bean
	FeePolicy feePolicy(@Value("${festa.fees.service-fee-bps}") int basisPoints,
			@Value("${festa.fees.service-fee-min-cents}") long minimumCents) {
		return new FeePolicy(basisPoints, minimumCents);
	}

}
