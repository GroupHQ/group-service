package org.grouphq.groupservice.group.domain.exceptions;

/**
 * Business exception informing that an action cannot be completed
 * because the server has encountered an unexpected error.
 */
public class InternalServerError extends RuntimeException {
    public InternalServerError() {
        super("""
            The server has encountered an unexpected error.
            Rest assured, this will be investigated.
            """);
    }
}
