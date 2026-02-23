package com.parkomfy.service;

import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CV failure logging for jury/review: categories and structured log entries.
 */
public class CVFailureLog {

    private static final Logger LOGGER = Logger.getLogger(CVFailureLog.class.getName());

    public enum FailureCategory {
        LOW_CONFIDENCE,
        BAD_ANGLE,
        OCCLUSION,
        INVALID_GEOMETRY,
        NO_DETECTION,
        OCR_MISREAD
    }

    private final FailureCategory category;
    private final String message;
    private final LocalDateTime timestamp;
    private final String cameraId;
    private final String slotId;
    private final String details;

    public CVFailureLog(FailureCategory category, String message,
                        String cameraId, String slotId, String details) {
        this.category = category;
        this.message = message != null ? message : category.name();
        this.timestamp = LocalDateTime.now();
        this.cameraId = cameraId;
        this.slotId = slotId;
        this.details = details;
    }

    public FailureCategory getCategory() {
        return category;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getCameraId() {
        return cameraId;
    }

    public String getSlotId() {
        return slotId;
    }

    public String getDetails() {
        return details;
    }

    /** Log this entry at WARNING level. */
    public void log(Logger logger) {
        if (logger != null) {
            logger.log(Level.WARNING, toString());
        }
    }

    /** Create and log a CV failure entry. */
    public static void logFailure(FailureCategory category, String message,
                                  String cameraId, String slotId, String details) {
        CVFailureLog entry = new CVFailureLog(category, message, cameraId, slotId, details);
        entry.log(LOGGER);
    }

    @Override
    public String toString() {
        return String.format("[CVFailure] category=%s cameraId=%s slotId=%s time=%s message=%s details=%s",
                category, cameraId, slotId, timestamp, message, details);
    }
}
