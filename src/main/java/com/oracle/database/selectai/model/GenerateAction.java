/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

/**
 * Select AI generation actions supported by {@code DBMS_CLOUD_AI.GENERATE}.
 */
public enum GenerateAction {
    /** Generate and run SQL for the natural language prompt. */
    runsql,
    /** Generate SQL text without executing it. */
    showsql,
    /** Generate SQL and return an explanation. */
    explainsql,
    /** Generate a natural-language narration of the answer. */
    narrate,
    /** Summarize content through the active profile. */
    summarize,
    /** Translate text through the active profile. */
    translate,
    /** Chat with the configured AI model. */
    chat,
    /** Generate embeddings for the prompt/content through the active profile. */
    embedding,
    /** Return the provider-facing prompt assembled by Select AI. */
    showprompt
}
