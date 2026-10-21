/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailableTeamTest {
    @Test
    void retainsReadOnlyDiscoverySnapshot() {
        AvailableTeam team = new AvailableTeam("OWNER.TEAM", "Shared support", "{\"name\":\"OWNER.TEAM\"}");

        assertThat(team.getTeamName()).isEqualTo("OWNER.TEAM");
        assertThat(team.getDescription()).isEqualTo("Shared support");
        assertThat(team.getRawJson()).isEqualTo("{\"name\":\"OWNER.TEAM\"}");
    }

    @Test
    void rejectsBlankTeamName() {
        assertThatThrownBy(() -> new AvailableTeam(" ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("teamName must not be null or blank");
    }
}
