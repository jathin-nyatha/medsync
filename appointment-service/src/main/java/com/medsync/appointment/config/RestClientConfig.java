package com.medsync.appointment.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

	@Bean
	public RestClient medSyncRestClient() {

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

		requestFactory.setConnectTimeout(Duration.ofSeconds(3));
		requestFactory.setReadTimeout(Duration.ofSeconds(5));

		return RestClient.builder().requestFactory(requestFactory).build();
	}
}