
package com.medsync.appointment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.client.RestClient;

class RestClientConfigTests {

	@Test
	void createsRestClientUsingDefaultTimeouts() {

		try (AnnotationConfigApplicationContext context = createContext(Map.of())) {

			assertThat(context.getBean(RestClient.class)).isNotNull();
		}
	}

	@Test
	void createsRestClientUsingCustomTimeouts() {

		Map<String, Object> properties = Map.of("medsync.clients.connect-timeout", "5s", "medsync.clients.read-timeout",
				"10s");

		try (AnnotationConfigApplicationContext context = createContext(properties)) {

			assertThat(context.getBean(RestClient.class)).isNotNull();
		}
	}

	@Test
	void rejectsZeroConnectTimeout() {

		assertInvalidTimeout("medsync.clients.connect-timeout", "0s", "connect-timeout must be greater than zero.");
	}

	@Test
	void rejectsNegativeConnectTimeout() {

		assertInvalidTimeout("medsync.clients.connect-timeout", "-1s", "connect-timeout must be greater than zero.");
	}

	@Test
	void rejectsZeroReadTimeout() {

		assertInvalidTimeout("medsync.clients.read-timeout", "0s", "read-timeout must be greater than zero.");
	}

	@Test
	void rejectsNegativeReadTimeout() {

		assertInvalidTimeout("medsync.clients.read-timeout", "-1s", "read-timeout must be greater than zero.");
	}

	private AnnotationConfigApplicationContext createContext(Map<String, Object> properties) {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

		context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());

		if (!properties.isEmpty()) {
			context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testProperties", properties));
		}

		context.register(RestClientConfig.class);
		context.refresh();

		return context;
	}

	private void assertInvalidTimeout(String propertyName, String value, String expectedMessage) {

		Map<String, Object> properties = Map.of(propertyName, value);

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

		context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());

		context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testProperties", properties));

		context.register(RestClientConfig.class);

		try {
			assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalArgumentException.class)
					.hasRootCauseMessage(expectedMessage);
		} finally {
			context.close();
		}
	}
}
