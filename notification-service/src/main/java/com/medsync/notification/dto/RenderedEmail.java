package com.medsync.notification.dto;

public record RenderedEmail(String recipientEmail, String subject, String plainTextContent, String htmlContent) {
}