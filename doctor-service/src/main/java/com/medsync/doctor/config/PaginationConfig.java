package com.medsync.doctor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class PaginationConfig {

	private static final int DEFAULT_PAGE_SIZE = 10;
	private static final int MAX_PAGE_SIZE = 100;

	@Bean
	public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {

		return pageableResolver -> {
			pageableResolver.setMaxPageSize(MAX_PAGE_SIZE);
			pageableResolver.setFallbackPageable(PageRequest.of(0, DEFAULT_PAGE_SIZE));
		};
	}
}