/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.profile;

import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.integration.IntegrationTestFixture;
import com.oracle.database.selectai.model.Feedback;
import com.oracle.database.selectai.model.GenerateAction;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.ProfileStatus;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live profile lifecycle and attribute integration coverage.
 *
 * <p>The shared fixture supplies environment loading, JDBC
 * setup, isolated resource names, and cleanup. Tests exercise profile
 * creation, retrieval, attribute updates, status changes, and lifecycle
 * behavior.</p>
 */

/** Integration coverage for ProfileLifecycle. */
class ProfileLifecycleIT extends ProfileIntegrationFixture {

    /**
     * Test: Create a profile and call {@code create()} on a second configured profile with the
     * same name.
     * Expected: The first creation succeeds; the duplicate creation throws SelectAIException
     * whose database cause contains an Oracle error and the original profile remains present.
     */
    @Test
    void test12400ProfileDuplicateCreateFails() throws Exception {
        String name = uniqueProfileName("PROFILE_DUPLICATE_CREATE");
        createManagedProfile(name, profileTestAttributes(), null);
        Profile duplicate = selectAI.profile(name, profileTestAttributes(), null, null);

        assertThatThrownBy(duplicate::create)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception.getCause()).hasMessageContaining(
                                    "ORA-20046: Profile " + name + " already exists.");
                        });
        assertThat(selectAI.profile(name).getProfileName()).isEqualTo(name);
    }

    /**
     * Test: Request a profile with a special-character name that is not present in the database.
     * Expected: Java lookup throws SelectAIException with the exact profile-not-found message.
     */
    @Test
    void test12401ProfileLookupWithSpecialCharacterNameUsesNotFoundContract() throws Exception {
        String name = "@@invalid!!";

        assertThatThrownBy(() -> selectAI.profile(name))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception).hasMessage("SelectAI profile not found: " + name);
                        });
    }

    /**
     * Test: Request a profile with a Unicode name that is not present in the database.
     * Expected: Java lookup throws SelectAIException with the exact profile-not-found message.
     */
    @Test
    void test12402ProfileLookupWithUnicodeNameUsesNotFoundContract() throws Exception {
        String name = "テスト";

        assertThatThrownBy(() -> selectAI.profile(name))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception).hasMessage("SelectAI profile not found: " + name);
                        });
    }

    /**
     * Test: Request a profile with a 150-character name that is not present in the database.
     * Expected: Java passes the name through without truncation and lookup throws the exact
     * profile-not-found message.
     */
    @Test
    void test12403ProfileLookupWithOverlongNameUsesNotFoundContract() throws Exception {
        String name = "P".repeat(150);

        assertThatThrownBy(() -> selectAI.profile(name))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception).hasMessage("SelectAI profile not found: " + name);
                        });
    }

    /**
     * Test: Creates a uniquely named profile, calls {@code drop(true)}, and then calls
     * {@code selectAI.profile(name)} for the removed name.
     * Expected: The forced drop returns {@code true}; the subsequent lookup throws
     * {@code SelectAIException} with {@code "SelectAI profile not found: <name>"}.
     */
    @Test
    void test12404ProfileDropWithForceTrueRemovesProfile() throws Exception {
        String name = uniqueProfileName("PROFILE_DROP_FORCE_TRUE");
        Profile target = createManagedProfile(name, profileTestAttributes(), null);

        assertThat(target.drop(true)).isTrue();
        assertThatThrownBy(() -> selectAI.profile(name))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("SelectAI profile not found: " + name);
    }

    /**
     * Test: Creates a uniquely named profile, calls {@code drop(false)}, and then looks it up by
     * name through the Select AI factory.
     * Expected: The non-forced drop returns {@code true}; the subsequent lookup throws
     * {@code SelectAIException} with the profile-not-found message for that name.
     */
    @Test
    void test12405ProfileDropWithForceFalseRemovesProfile() throws Exception {
        String name = uniqueProfileName("PROFILE_DROP_FORCE_FALSE");
        Profile target = createManagedProfile(name, profileTestAttributes(), null);

        assertThat(target.drop(false)).isTrue();
        assertThatThrownBy(() -> selectAI.profile(name))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("SelectAI profile not found: " + name);
    }

    /**
     * Test: Create a profile, call {@code drop(true)}, and call {@code drop(true)} again on the
     * same object.
     * Expected: Both forced drops return true and the profile remains absent.
     */
    @Test
    void test12406ProfileRepeatedForceDropIsIdempotent() throws Exception {
        String name = uniqueProfileName("PROFILE_DROP_REPEAT_FORCE");
        Profile target = createManagedProfile(name, profileTestAttributes(), null);

        assertThat(target.drop(true)).isTrue();
        assertThat(target.drop(true)).isTrue();
        assertThatThrownBy(() -> selectAI.profile(name))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception).hasMessage("SelectAI profile not found: " + name);
                        });
    }

    /**
     * Test: Create a profile, call {@code drop(false)}, and call {@code drop(false)} again on the
     * same object.
     * Expected: The first drop succeeds; the second non-forced drop throws SelectAIException with
     * a database error, and the profile remains absent.
     */
    @Test
    void test12407ProfileRepeatedNonForceDropFails() throws Exception {
        String name = uniqueProfileName("PROFILE_DROP_REPEAT_NONFORCE");
        Profile target = createManagedProfile(name, profileTestAttributes(), null);

        assertThat(target.drop(false)).isTrue();
        assertThatThrownBy(() -> target.drop(false))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception.getCause()).hasMessageContaining(
                                    "ORA-20046: Profile \"" + name + "\" does not exist.");
                        });
        assertThatThrownBy(() -> selectAI.profile(name))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception).hasMessage("SelectAI profile not found: " + name);
                        });
    }

    /**
     * Test: Creates one profile, obtains a second handle for the same database row, drops it with
     * the first handle, and calls {@code getStatus()} on the second handle.
     * Expected: The second handle refresh fails with {@code SelectAIException} containing the
     * profile-not-found message, proving it does not report stale metadata as current.
     */
    @Test
    void test12408ProfileDatabaseOperationFailsOnSecondReferenceAfterFirstDrops() throws Exception {
        String name = uniqueProfileName("PROFILE_SHARED_DROP");
        Profile first = createManagedProfile(name, profileTestAttributes(), null);
        Profile second = selectAI.profile(name);

        assertThat(first.drop(true)).isTrue();

        assertThatThrownBy(second::getStatus)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("SelectAI profile not found: " + name);
    }

    /**
     * Test: Creates an enabled profile, calls {@code disable()}, and retrieves its status through
     * a newly fetched handle.
     * Expected: {@code disable()} returns {@code true} and the database-backed handle reports the
     * disabled status value.
     */
    @Test
    void test12409ProfileDisablePersistsDisabledStatus() throws Exception {
        String name = uniqueProfileName("PROFILE_DISABLE");
        Profile target = createManagedProfile(name, profileTestAttributes(), null);

        assertThat(target.disable()).isTrue();
        assertThat(selectAI.profile(name).getStatus())
                .isEqualTo(ProfileStatus.DISABLED.getValue());
    }

    /**
     * Test: Creates a profile with initial status {@code DISABLED}, calls {@code enable()}, and
     * reads status from a newly fetched handle.
     * Expected: {@code enable()} returns {@code true} and the database-backed handle reports the
     * enabled status value.
     */
    @Test
    void test12410ProfileEnablePersistsEnabledStatus() throws Exception {
        String name = uniqueProfileName("PROFILE_ENABLE");
        Profile target = createManagedProfile(name, profileTestAttributes(), null,
                ProfileStatus.DISABLED);

        assertThat(target.enable()).isTrue();
        assertThat(selectAI.profile(name).getStatus())
                .isEqualTo(ProfileStatus.ENABLED.getValue());
    }

    /**
     * Test: Create an enabled profile, call {@code disable()}, and call {@code disable()} again.
     * Expected: The first call succeeds; the second call throws SelectAIException with the
     * database's already-disabled error.
     */
    @Test
    void test12411ProfileDisableTwiceFails() throws Exception {
        String name = uniqueProfileName("PROFILE_DISABLE_TWICE");
        Profile target = createManagedProfile(name, profileTestAttributes(), null,
                ProfileStatus.ENABLED);

        assertThat(target.disable()).isTrue();
        assertThatThrownBy(target::disable)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception.getCause()).hasMessageContaining(
                                    "ORA-20000: Profile is already in the desired status");
                        });
    }

    /**
     * Test: Create a disabled profile, call {@code enable()}, and call {@code enable()} again.
     * Expected: The first call succeeds; the second call throws SelectAIException with the
     * database's already-enabled error.
     */
    @Test
    void test12412ProfileEnableTwiceFails() throws Exception {
        String name = uniqueProfileName("PROFILE_ENABLE_TWICE");
        Profile target = createManagedProfile(name, profileTestAttributes(), null,
                ProfileStatus.DISABLED);

        assertThat(target.enable()).isTrue();
        assertThatThrownBy(target::enable)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception.getCause()).hasMessageContaining(
                                    "ORA-20000: Profile is already in the desired status");
                        });
    }

    /**
     * Test: Creates an enabled profile, opens a second handle, disables the profile through the
     * second handle, and then calls {@code getStatus()} on the original handle.
     * Expected: The original handle refreshes its metadata and returns the database's disabled
     * status value instead of its previously observed enabled value.
     */
    @Test
    void test12413ProfileGetStatusRefreshesExistingObjectAfterExternalDisable() throws Exception {
        String name = uniqueProfileName("PROFILE_STATUS_REFRESH");
        createManagedProfile(name, profileTestAttributes(), null, ProfileStatus.ENABLED);

        Profile staleProfile = selectAI.profile(name);
        assertThat(staleProfile.getStatus()).isEqualTo(ProfileStatus.ENABLED.getValue());

        Profile updater = selectAI.profile(name);
        assertThat(updater.disable()).isTrue();

        assertThat(staleProfile.getStatus()).isEqualTo(ProfileStatus.DISABLED.getValue());
    }
}
