package com.giri.oms.common.exception;

import java.time.LocalDateTime;

/**
 * @param errorCode the 6-character {@code [PREFIX][COMPONENT_ID][ERROR_ID]} code for
 *                   client-side branching, e.g. {@code "EPR100"} — see {@link ErrorCode}
 *                   for the full set, the format, and the stability contract.
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String errorCode,
        String message,
        String path
) {}
