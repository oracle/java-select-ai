/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

/**
 * Object types accepted by object-type parameters in {@code DBMS_CLOUD_AI_AGENT}.
 *
 * @since 1.1.0
 */
public enum AgentObjectType {
    /** Agent object. */
    AGENT,
    /** Task object. */
    TASK,
    /** Tool object. */
    TOOL,
    /** Team object. */
    TEAM
}
