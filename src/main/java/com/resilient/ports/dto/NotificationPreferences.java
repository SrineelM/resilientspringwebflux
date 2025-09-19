/**
 * Data transfer object (DTO) representing a user's notification preferences.
 * <p>
 * Used to specify which channels (email, SMS, etc.) a user wants to receive notifications on.
 *
 * @param channel The preferred notification channel (e.g., "email", "sms", "push")
 * @param email Whether email notifications are enabled
 * @param sms Whether SMS notifications are enabled
 */
package com.resilient.ports.dto;

public record NotificationPreferences(String channel, boolean email, boolean sms) {}
