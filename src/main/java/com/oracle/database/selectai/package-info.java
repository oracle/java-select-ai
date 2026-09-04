/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

/**
 * Public API for using Oracle Select AI from Java applications.
 * <p>
 * The package provides synchronous interfaces for creating and opening Select
 * AI resources, including profiles, credentials, conversations, and vector
 * indexes. It also provides profile-scoped prompt generation and
 * conversation-session operations, together with the separate
 * {@link com.oracle.database.selectai.DatabaseAdmin} API for privileged database setup.
 * <p>
 * Obtain SDK clients through the public factory methods on
 * {@link com.oracle.database.selectai.SelectAI},
 * {@link com.oracle.database.selectai.DatabaseAdmin}, or
 * {@link com.oracle.database.selectai.DbConnection}. Classes in implementation packages
 * are not part of the supported public API.
 *
 * @see com.oracle.database.selectai.SelectAI
 * @see com.oracle.database.selectai.DatabaseAdmin
 * @see com.oracle.database.selectai.DbConnection
 */
package com.oracle.database.selectai;
