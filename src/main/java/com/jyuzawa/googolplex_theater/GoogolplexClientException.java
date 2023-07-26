/*
 * Copyright (c) 2023 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

public class GoogolplexClientException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public GoogolplexClientException(String message) {
        super(message);
    }

    public GoogolplexClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
