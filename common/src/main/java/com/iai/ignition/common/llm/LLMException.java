package com.iai.ignition.common.llm;

/**
 * Exception thrown when LLM operations fail.
 */
public class LLMException extends Exception {
    private static final long serialVersionUID = 1L;

    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
