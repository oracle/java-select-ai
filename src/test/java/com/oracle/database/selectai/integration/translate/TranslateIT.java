/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.translate;

import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Live integration tests for profile translation and language selection.
 */
class TranslateIT extends TranslationIntegrationFixture {

    /**
     * Test: On the fixture-created profile configured with the ADMIN-owned PEOPLE
     * and GYMNAST tables, call
     * {@code translate("Thank you", "en", "de")}.
     * Expected: The provider returns a non-blank translation containing {@code "Danke"} after
     * lower-casing. The inherited fixture cleanup drops the profile and temporary credential.
     */
    @Test
    void test15000TranslatesEnglishTextToGermanUsingTheCreatedProfile() throws Exception {
        String translated = profile.translate(TRANSLATE_TEXT, SOURCE_LANGUAGE, TARGET_LANGUAGE);

        assertThat(translated).isNotBlank();
        assertThat(translated.toLowerCase()).contains(EXPECTED_TRANSLATION.toLowerCase());
    }

    /**
     * Test: Update the database-backed profile with {@code source_language="en"} and
     * {@code target_language="de"}, then call {@code translate("Thank you", null, null)} so
     * both method language arguments are omitted and the profile attributes must supply them.
     * Expected: Both setters return {@code true}; the provider returns a non-blank translation
     * containing {@code "danke"} after lower-casing. The inherited fixture cleanup drops the
     * profile and temporary credential.
     */
    @Test
    void test15001TranslatesUsingProfileLanguageAttributesWhenArgumentsAreNull() throws Exception {
        assertThat(profile.setAttribute("source_language", SOURCE_LANGUAGE)).isTrue();
        assertThat(profile.setAttribute("target_language", TARGET_LANGUAGE)).isTrue();

        String translated = profile.translate(TRANSLATE_TEXT, null, null);

        assertThat(translated).isNotBlank();
        assertThat(translated.toLowerCase()).contains(EXPECTED_TRANSLATION.toLowerCase());
    }

    /**
     * Test: Set the persisted profile attribute {@code target_language="de"} and call
     * {@code translate("Thank you", "en", null)}, supplying English explicitly as the source
     * while leaving the target method argument null.
     * Expected: The setter returns {@code true}; the provider uses the profile's German target
     * attribute and returns a non-blank translation containing {@code "danke"} after
     * lower-casing. The inherited fixture cleanup removes the profile and temporary credential.
     */
    @Test
    void test15002TranslatesUsingProfileTargetWhenTargetArgumentIsNull() throws Exception {
        assertThat(profile.setAttribute("target_language", TARGET_LANGUAGE)).isTrue();

        String translated = profile.translate(TRANSLATE_TEXT, SOURCE_LANGUAGE, null);

        assertThat(translated).isNotBlank();
        assertThat(translated.toLowerCase()).contains(EXPECTED_TRANSLATION.toLowerCase());
    }

    /**
     * Test: Set profile attributes {@code source_language="fr"} and
     * {@code target_language="fr"}, then call
     * {@code translate("Thank you", "en", "de")}; the explicit German target intentionally
     * and English source intentionally differ from the persisted French values.
     * Expected: Both setters return {@code true}, and the provider honors both explicit method
     * arguments rather than the stored French values, returning a non-blank translation that
     * contains {@code "danke"} after lower-casing. The inherited fixture cleanup drops the
     * profile and temporary credential.
     */
    @Test
    void test15003ExplicitLanguageArgumentsOverrideProfileLanguageAttributes() throws Exception {
        assertThat(profile.setAttribute("source_language", "fr")).isTrue();
        assertThat(profile.setAttribute("target_language", "fr")).isTrue();

        String translated = profile.translate(TRANSLATE_TEXT, SOURCE_LANGUAGE, TARGET_LANGUAGE);

        assertThat(translated).isNotBlank();
        assertThat(translated.toLowerCase()).contains(EXPECTED_TRANSLATION.toLowerCase());
    }

    /**
     * Test: Configure only the profile target language and call the one-argument overload.
     * Expected: The database uses the profile target, detects the source language, and returns
     * the German translation.
     */
    @Test
    void test15004TranslatesUsingProfileLanguageWithSingleArgumentOverload() throws Exception {
        assertThat(profile.setAttribute("target_language", TARGET_LANGUAGE)).isTrue();

        String translated = profile.translate(TRANSLATE_TEXT);

        assertThat(translated).isNotBlank();
        assertThat(translated.toLowerCase()).contains(EXPECTED_TRANSLATION.toLowerCase());
    }

    /**
     * Test: Call the two-argument overload with an explicit German target and no source language.
     * Expected: The database uses the explicit target and detects the source language.
     */
    @Test
    void test15005TranslatesWithExplicitTargetUsingTwoArgumentOverload() throws Exception {
        String translated = profile.translate(TRANSLATE_TEXT, TARGET_LANGUAGE);

        assertThat(translated).isNotBlank();
        assertThat(translated.toLowerCase()).contains(EXPECTED_TRANSLATION.toLowerCase());
    }

    /**
     * Test: Call translate without a target argument and without a target_language profile
     * attribute.
     * Expected: DBMS_CLOUD_AI rejects the request and the database error is surfaced as a
     * SelectAIException.
     */
    @Test
    void test15006TranslateRejectsMissingTargetLanguageWhenParameterAndProfileAreUnset() throws Exception {
        assertThatThrownBy(() -> profile.translate(TRANSLATE_TEXT, SOURCE_LANGUAGE, null))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                    assertThat(exception.getCause().getMessage())
                            .startsWith("ORA-20000: No target translation language is provided");
                });
    }

    /**
     * Test: Call {@code translate(TRANSLATE_TEXT, null, TARGET_LANGUAGE)} with no source-language argument
     * and an explicit German target-language argument; no profile language attributes are set
     * by this test.
     * Expected: The provider detects the source language, translates to German, and returns a
     * non-blank result containing {@code "danke"} after lower-casing. The inherited fixture
     * cleanup drops the profile and temporary credential.
     */
    @Test
    void test15007ProviderDetectsSourceLanguageWhenSourceArgumentIsNull() throws Exception {
        String translated = profile.translate(TRANSLATE_TEXT, null, TARGET_LANGUAGE);

        assertThat(translated).isNotBlank();
        assertThat(translated.toLowerCase()).contains(EXPECTED_TRANSLATION.toLowerCase());
    }

    /**
     * Test: Supply an invalid source language while supplying a valid German target.
     * Expected: DBMS_CLOUD_AI rejects the invalid source language and the database error is
     * surfaced as a SelectAIException.
     */
    @Test
    void test15008TranslateRejectsInvalidSourceLanguage() throws Exception {
        assertThatThrownBy(() -> profile.translate(
                TRANSLATE_TEXT, "not-a-real-language", TARGET_LANGUAGE))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                    assertThat(exception.getCause().getMessage())
                            .contains("Invalid language or language code");
                });
    }

    /**
     * Test: Supply language names and mixed-case language values.
     * Expected: Both forms are accepted because language names and codes are case-insensitive.
     */
    @Test
    void test15009TranslateAcceptsLanguageNamesAndMixedCaseValues() throws Exception {
        String namedTranslation = profile.translate(TRANSLATE_TEXT, "English", "German");
        String mixedCaseTranslation = profile.translate(TRANSLATE_TEXT, "eNgLiSh", "gErMaN");

        assertThat(namedTranslation).isNotBlank();
        assertThat(namedTranslation.toLowerCase()).contains(EXPECTED_TRANSLATION.toLowerCase());
        assertThat(mixedCaseTranslation).isNotBlank();
        assertThat(mixedCaseTranslation.toLowerCase())
                .contains(EXPECTED_TRANSLATION.toLowerCase());
    }

    /**
     * Test: Create a second profile with source_language and target_language in the create payload,
     * then omit both method language arguments.
     * Expected: DBMS_CLOUD_AI uses the create-time profile attributes for translation.
     */
    @Test
    void test15010TranslatesUsingLanguageAttributesSuppliedAtProfileCreation() throws Exception {
        Map<String, String> attributeMap = new HashMap<>(isolatedProfileAttributes().toAttributeMap());
        attributeMap.put("source_language", SOURCE_LANGUAGE);
        attributeMap.put("target_language", TARGET_LANGUAGE);
        ProfileAttributes attributes = ProfileAttributes.fromAttributeMap(attributeMap);

        var createdProfile = createManagedProfile(
                profileName + "_ATTR", attributes, "Create-time translation attributes");
        String translated = createdProfile.translate(TRANSLATE_TEXT, null, null);

        assertThat(translated).isNotBlank();
        assertThat(translated.toLowerCase()).contains(EXPECTED_TRANSLATION.toLowerCase());
    }

    /**
     * Test: Call {@code translate} with TRANSLATE_TEXT, SOURCE_LANGUAGE, and
     * a target-language argument containing only two spaces ({@code "  "}).
     * Expected: The database-backed call throws {@code SelectAIException}; its cause is a
     * {@code SQLException} whose message starts with
     * {@code "ORA-20000: No target translation language is provided"}. The inherited fixture
     * cleanup drops the profile and temporary credential.
     */
    @Test
    void test15011TranslateRejectsBlankTargetLanguage() throws Exception {
        assertThatThrownBy(() -> profile.translate(TRANSLATE_TEXT, SOURCE_LANGUAGE, "  "))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                    assertThat(exception.getCause().getMessage())
                            .startsWith("ORA-20000: No target translation language is provided");
                });
    }

    /**
     * Test: Call {@code translate} with TRANSLATE_TEXT, SOURCE_LANGUAGE, and
     * the unsupported target-language value {@code "not-a-real-language"}.
     * Expected: The database-backed call throws {@code SelectAIException}; its cause is a
     * {@code SQLException} whose message starts with
     * {@code "ORA-20000: Invalid language or language code \""}. The inherited fixture cleanup
     * drops the profile and temporary credential.
     */
    @Test
    void test15012TranslateRejectsUnsupportedTargetLanguage() throws Exception {
        assertThatThrownBy(() -> profile.translate(
                TRANSLATE_TEXT, SOURCE_LANGUAGE, "not-a-real-language"))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                    assertThat(exception.getCause().getMessage())
                            .startsWith("ORA-20000: Invalid language or language code \"");
                });
    }
}
