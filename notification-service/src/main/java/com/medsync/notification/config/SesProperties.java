
package com.medsync.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "medsync.notification.ses")
public record SesProperties(String region, String fromEmail, boolean enabled) {
}
