package com.festa.shared.time;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga os jobs {@code @Scheduled} (virada de lote; depois expiração de pedidos). */
@Configuration
@EnableScheduling
public class SchedulingConfig {

}
