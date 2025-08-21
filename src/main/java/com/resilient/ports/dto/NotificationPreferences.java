package com.resilient.ports.dto;

public record NotificationPreferences(String channel, boolean email, boolean sms) {}
