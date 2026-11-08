
package com.medsync.appointment.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

	@Bean
	public RestClient medSyncRestClient(@Value("${medsync.clients.connect-timeout:3s}") Duration connectTimeout,

			@Value("${medsync.clients.read-timeout:5s}") Duration readTimeout) {

		validateTimeout("connect-timeout", connectTimeout);
		validateTimeout("read-timeout", readTimeout);

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

		requestFactory.setConnectTimeout(connectTimeout);
		requestFactory.setReadTimeout(readTimeout);

		return RestClient.builder().requestFactory(requestFactory).build();
	}

	private void validateTimeout(String propertyName, Duration timeout) {

		if (timeout == null || timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException(propertyName + " must be greater than zero.");
		}
	}
}
