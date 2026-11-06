
package com.medsync.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@EnableConfigurationProperties(SesProperties.class)
public class SesConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "medsync.notification.ses", name = "enabled", havingValue = "true")
	public SesV2Client sesV2Client(SesProperties properties) {

		if (properties.region() == null || properties.region().isBlank()) {
			throw new IllegalStateException("AWS SES region must be configured when SES is enabled.");
		}

		if (properties.fromEmail() == null || properties.fromEmail().isBlank()) {
			throw new IllegalStateException("SES sender email must be configured when SES is enabled.");
		}

		return SesV2Client.builder().region(software.amazon.awssdk.regions.Region.of(properties.region())).build();
	}
}
