/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

final class LogCapture {

    private LogCapture() {
    }

    static CapturedFailure captureFailure(ThrowingCallable operation) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        try (PrintStream replacementErr = new PrintStream(capturedErr, true, StandardCharsets.UTF_8)) {
            System.setErr(replacementErr);
            Throwable throwable = catchThrowable(operation);
            replacementErr.flush();
            return new CapturedFailure(throwable,
                    new String(capturedErr.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            System.setErr(originalErr);
        }
    }

    static void assertFailureDoesNotExpose(CapturedFailure failure, String... sensitiveValues) {
        assertThat(failure.throwable()).isNotNull();
        for (String sensitiveValue : sensitiveValues) {
            assertThat(failure.throwable().getMessage()).doesNotContain(sensitiveValue);
            assertThat(failure.throwable().toString()).doesNotContain(sensitiveValue);
            assertThat(failure.logs()).doesNotContain(sensitiveValue);
        }
    }

    record CapturedFailure(Throwable throwable, String logs) {
    }
}
