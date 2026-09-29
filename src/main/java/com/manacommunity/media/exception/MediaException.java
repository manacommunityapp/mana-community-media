package com.manacommunity.media.exception;

/**
 * Domain exception for all media-service business rule violations.
 */
public class MediaException extends RuntimeException {

    public MediaException(String message) {
        super(message);
    }

    public MediaException(String message, Throwable cause) {
        super(message, cause);
    }
}
