package com.itdesk;

/** An error that should be shown to the caller with a specific HTTP status. */
final class ApiError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    final int status;

    ApiError(int status, String message) {
        super(message, null, false, false);
        this.status = status;
    }
}
