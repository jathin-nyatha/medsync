package com.medsync.notification.dto;

public record EmailMessage(String to, String subject, String plainTextContent, String htmlContent) {
}