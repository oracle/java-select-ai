/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.generate;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.GenerateAction;
import com.oracle.database.selectai.model.GenerateParams;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Live integration tests for profile generation actions and request options.
 */
class GenerateIT extends GenerateIntegrationFixture {

    private static final String[] PROMPTS = {
            "How many gymnasts in database?",
            "How many people are there in the database?"
    };

    private static final String SUMMARY_CONTENT = """
            A gas cloud in our galaxy contains enough alcohol to brew
            hundreds of trillions of pints of beer. Some stars are cool enough
            to touch, while high pressure creates hot ice on distant exoplanets.
            """;

    @Override
    protected String profileObjectList() {
        return objectListFor("people", "gymnast");
    }

    @BeforeEach
    @Override
    protected void createIsolatedProfile() throws Exception {
        super.createIsolatedProfile();
        profile.setAttribute("model", GENERATE_MODEL);
    }

    /**
     * Test: On the fixture-created profile, whose object list contains the PEOPLE and GYMNAST
     * tables and whose model is set to {@code openai.gpt-5.6-luna}, call {@code showsql} for
     * both prompts: {@code "How many gymnasts in database?"} and
     * {@code "How many people are there in the database?"}.
     * Expected: Each database-backed response is non-blank and contains the word
     * {@code SELECT}, ignoring case. The inherited fixture cleanup then drops the profile and
     * its temporary credential.
     */
    @Test
    void test16000ShowSql() throws Exception {
        for (int i = 0; i < PROMPTS.length; i++) {
            assertThat(profile.showsql(PROMPTS[i])).isNotBlank().containsIgnoringCase("select");
        }
    }

    /**
     * Test: Call {@code showprompt} on the fixture-created profile for
     * {@code "How many gymnasts in database?"} and
     * {@code "How many people are there in the database?"}. The profile is configured with
     * an enforced object list containing the PEOPLE and GYMNAST tables.
     * Expected: Each generated prompt is non-blank and contains the escaped qualified names
     * {@code \"ADMIN\".\"PEOPLE\"} and {@code \"ADMIN\".\"GYMNAST\"}, proving that the
     * database uses both configured objects. The inherited fixture cleanup removes the profile
     * and temporary credential.
     */
    @Test
    void test16001ShowPrompt() throws Exception {
        for (int i = 0; i < PROMPTS.length; i++) {
            assertThat(profile.showprompt(PROMPTS[i]))
                    .isNotBlank()
                    .contains("\\\"ADMIN\\\".\\\"PEOPLE\\\"")
                    .contains("\\\"ADMIN\\\".\\\"GYMNAST\\\"");
        }
    }

    /**
     * Test: Call {@code runsql} for {@code "How many gymnasts in database?"} and
     * {@code "How many people are there in the database?"} on the profile whose enforced
     * object list points to the PEOPLE and GYMNAST fixture tables.
     * Expected: Each database-executed response is non-blank and, after lower-casing, contains
     * either {@code 5} or {@code five}, matching the five rows loaded into the fixture tables.
     * The inherited fixture cleanup drops the created profile and credential.
     */
    @Test
    void test16002RunSql() throws Exception {
        assertThat(profile.runsql(PROMPTS[0]))
                .isNotBlank()
                .satisfies(response -> assertThat(response.toLowerCase())
                        .containsAnyOf("5", "five"));
        assertThat(profile.runsql(PROMPTS[1]))
                .isNotBlank()
                .satisfies(response -> assertThat(response.toLowerCase())
                        .containsAnyOf("5", "five"));
    }

    /**
     * Test: Call the profile's chat operation with the exact natural-language prompt
     * {@code "What is 4 + 4 ?"}; no request-level attributes or conversation parameters are
     * supplied.
     * Expected: The provider-backed response is non-blank and contains {@code 8}, the answer
     * to the supplied arithmetic question. The inherited fixture cleanup drops the profile and
     * temporary credential.
     */
    @Test
    void test16003Chat() throws Exception {
        assertThat(profile.chat("What is 4 + 4 ?"))
                .isNotBlank()
                .contains("8");
    }

    /**
     * Test: Call {@code narrate} for both configured prompts,
     * {@code "How many gymnasts in database?"} and
     * {@code "How many people are there in the database?"}, using the fixture-created profile
     * and its enforced PEOPLE/GYMNAST object list.
     * Expected: Each provider-generated narration is non-blank and, after lower-casing,
     * contains either {@code 5} or {@code five}, reflecting the five fixture rows. The
     * inherited fixture cleanup removes the profile and temporary credential.
     */
    @Test
    void test16004Narrate() throws Exception {
        for (int i = 0; i < PROMPTS.length; i++) {
            assertThat(profile.narrate(PROMPTS[i]))
                    .isNotBlank()
                    .satisfies(response -> assertThat(response.toLowerCase())
                            .containsAnyOf("5", "five"));
        }
    }

    /**
     * Test: Call {@code explainsql} for each configured prompt—
     * {@code "How many gymnasts in database?"} and
     * {@code "How many people are there in the database?"}—using the created profile and its
     * enforced PEOPLE/GYMNAST object list.
     * Expected: The database/provider returns a non-blank explanation for each generated SQL
     * request. The inherited fixture cleanup drops the profile and temporary credential.
     */
    @Test
    void test16005ExplainSql() throws Exception {
        for (String prompt : PROMPTS) {
            assertThat(profile.explainsql(prompt)).isNotBlank();
        }
    }

    /**
     * Test: Call the generic {@code generate} API with prompt
     * {@code "How many gymnasts in database?"} and action {@code GenerateAction.runsql}, using
     * the fixture-created profile without request-level attributes or parameters.
     * Expected: The database executes the generated SQL and returns a non-blank response that
     * contains {@code 5}, the count represented by the five fixture rows. The inherited fixture
     * cleanup drops the profile and temporary credential.
     */
    @Test
    void test16006GenerateRunSql() throws Exception {
        assertThat(profile.generate(PROMPTS[0], GenerateAction.runsql))
                .isNotBlank()
                .contains("5");
    }

    /**
     * Test: Call the generic {@code generate} API with prompt
     * {@code "How many gymnasts in database?"} and action {@code GenerateAction.showsql}; no
     * request attributes or conversation parameters are passed.
     * Expected: The generated SQL response is non-blank and contains {@code SELECT}, ignoring
     * case. The inherited fixture cleanup removes the profile and temporary credential.
     */
    @Test
    void test16007GenerateShowSql() throws Exception {
        assertThat(profile.generate(PROMPTS[0], GenerateAction.showsql))
                .isNotBlank()
                .containsIgnoringCase("select");
    }

    /**
     * Test: Call the generic {@code generate} API with prompt {@code "What is 7 + 953"} and
     * action {@code GenerateAction.chat}, without request attributes or conversation parameters.
     * Expected: The provider-backed chat response is non-blank and contains {@code 960}, the
     * arithmetic result. The inherited fixture cleanup drops the profile and temporary
     * credential.
     */
    @Test
    void test16008GenerateChat() throws Exception {
        assertThat(profile.generate("What is 7 + 953", GenerateAction.chat))
                .isNotBlank()
                .contains("960");
    }

    /**
     * Test: Call the generic {@code generate} API with prompt
     * {@code "How many gymnasts in database?"} and action {@code GenerateAction.narrate}, using
     * the fixture-created profile and its enforced PEOPLE/GYMNAST object list.
     * Expected: The returned narration is non-blank and contains {@code 5}, the count expected
     * from the five fixture rows. The inherited fixture cleanup removes the profile and
     * temporary credential.
     */
    @Test
    void test16009GenerateNarrate() throws Exception {
        assertThat(profile.generate(PROMPTS[0], GenerateAction.narrate))
                .isNotBlank()
                .contains("5");
    }

    /**
     * Test: Call the generic {@code generate} API with action
     * {@code GenerateAction.explainsql} for both prompts, {@code "How many gymnasts in
     * database?"} and {@code "How many people are there in the database?"}.
     * Expected: Each database/provider explanation response is non-blank. The inherited fixture
     * cleanup drops the profile and temporary credential.
     */
    @Test
    void test16010GenerateExplainSql() throws Exception {
        for (String prompt : PROMPTS) {
            assertThat(profile.generate(prompt, GenerateAction.explainsql)).isNotBlank();
        }
    }

    /**
     * Test: Call the generic {@code generate} API with prompt
     * {@code "How many gymnasts in database?"}, action {@code GenerateAction.showprompt}, and
     * request-level {@code additionalInstructions} set to
     * {@code "JSAI request-specific instruction: answer concisely."}. The persisted profile
     * still has its PEOPLE/GYMNAST object list.
     * Expected: The generated prompt is non-blank and contains the exact request instruction.
     * Reading the profile attributes confirms that the persisted object list still contains
     * {@code people} and {@code gymnast}; the inherited fixture cleanup removes the profile and
     * temporary credential.
     */
    @Test
    void test16011GenerateWithRequestAttributes() throws Exception {
        String requestInstruction = "JSAI request-specific instruction: answer concisely.";
        ProfileAttributes requestAttributes = ProfileAttributes.builder()
                .additionalInstructions(requestInstruction)
                .build();

        String response = profile.generate(
                PROMPTS[0],
                GenerateAction.showprompt,
                requestAttributes);

        assertThat(response)
                .isNotBlank()
                .contains(requestInstruction);
        assertThat(profile.getProfileAttributes().getObjectList())
                .contains("people")
                .contains("gymnast");
        assertThat(profile.getProfileAttributes().getAdditionalInstructions()).isNull();
    }

    /**
     * Test: Call the generic {@code generate} API with prompt {@code "Oracle database"}, action
     * {@code GenerateAction.embedding}, and request-level {@code embeddingModel} set to
     * {@code "cohere.embed-english-v3.0"}.
     * Expected: The database/provider accepts the request-level embedding model and returns a
     * non-blank embedding response. The inherited fixture cleanup drops the profile and
     * temporary credential.
     */
    @Test
    void test16012GenerateEmbedding() throws Exception {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .embeddingModel("cohere.embed-english-v3.0")
                .build();

        assertThat(profile.generate(
                "Oracle database",
                GenerateAction.embedding,
                attributes))
                .isNotBlank();
    }

    /**
     * Test: Create a database-backed conversation titled
     * {@code JSAI_GENERATE_CONV_<UUID>}, pass its returned conversation ID through
     * {@code GenerateParams.conversationId}, and call the profile's generic {@code generate}
     * API with prompt {@code "What is a database ?"} and action {@code GenerateAction.chat}.
     * Expected: The chat response is non-blank. Listing prompts from the created conversation
     * returns at least one prompt, and every returned prompt has the same conversation ID that
     * was supplied in {@code GenerateParams}. The test's {@code finally} block drops the
     * conversation when creation produced an ID; the fixture then removes the profile and
     * temporary credential.
     */
    @Test
    void test16013GenerateWithConversationIdParams() throws Exception {
        Conversation conversation = selectAI.conversation(
                ConversationAttributes.builder()
                        .title("JSAI_GENERATE_CONV_" + UUID.randomUUID())
                        .build());

        try {
            String conversationId = conversation.create();
            GenerateParams params = GenerateParams.builder()
                    .conversationId(conversationId)
                    .build();

            assertThat(profile.generate(
                    "What is a database ?",
                    GenerateAction.chat,
                    params)).isNotBlank();

            assertThat(conversation.listPrompts())
                    .isNotEmpty()
                    .allSatisfy(prompt -> assertThat(prompt.getConversationId())
                            .isEqualTo(conversationId));
        } finally {
            if (conversation.getConversationId() != null) {
                conversation.drop(true);
            }
        }
    }

    /**
     * Test: Create a database-backed conversation titled
     * {@code JSAI_GENERATE_COMBINED_<UUID>}; pass its ID in
     * {@code GenerateParams.conversationId}; and use request-level
     * {@code additionalInstructions} set to
     * {@code "JSAI request-specific instruction: answer concisely."}. First call the generic
     * API with prompt {@code "What is a database ?"} and action
     * {@code GenerateAction.showprompt}, then call it again with the same prompt and action
     * {@code GenerateAction.chat}, passing the same request attributes and params each time.
     * Expected: The showprompt response is non-blank and contains the exact request instruction;
     * the chat response is non-blank; and listing prompts from the conversation returns at least
     * one prompt whose conversation ID equals the supplied ID. The test's {@code finally} block
     * drops the conversation, followed by inherited profile and credential cleanup.
     */
    @Test
    void test16014GenerateWithAttributesAndConversationIdParams() throws Exception {
        Conversation conversation = selectAI.conversation(
                ConversationAttributes.builder()
                        .title("JSAI_GENERATE_COMBINED_" + UUID.randomUUID())
                        .build());

        try {
            String conversationId = conversation.create();
            String requestInstruction = "JSAI request-specific instruction: answer concisely.";
            ProfileAttributes requestAttributes = ProfileAttributes.builder()
                    .additionalInstructions(requestInstruction)
                    .build();
            GenerateParams params = GenerateParams.builder()
                    .conversationId(conversationId)
                    .build();
            String response = profile.generate(
                    "What is a database ?",
                    GenerateAction.showprompt,
                    requestAttributes,
                    params);
            assertThat(response)
                    .isNotBlank()
                    .contains(requestInstruction);

            assertThat(profile.generate(
                    "What is a database ?",
                    GenerateAction.chat,
                    requestAttributes,
                    params)).isNotBlank();

            assertThat(conversation.listPrompts())
                    .isNotEmpty()
                    .allSatisfy(prompt -> assertThat(prompt.getConversationId())
                            .isEqualTo(conversationId));
        } finally {
            if (conversation.getConversationId() != null) {
                conversation.drop(true);
            }
        }
    }

    /**
     * Test: Call generic GENERATE with the documented summarize action and inline content.
     * Expected: The GENERATE route returns a non-blank summary different from the input.
     */
    @Test
    void test16015GenerateSummarize() throws Exception {
        assertThat(profile.generate(SUMMARY_CONTENT, GenerateAction.summarize))
                .isNotBlank()
                .isNotEqualTo(SUMMARY_CONTENT.trim());
    }

    /**
     * Test: Call generic GENERATE with the documented translate action and request-level English
     * source and German target language attributes.
     * Expected: The request-level language attributes reach GENERATE and produce a German
     * translation containing "Danke".
     */
    @Test
    void test16016GenerateTranslateWithRequestLanguages() throws Exception {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .sourceLanguage(SOURCE_LANGUAGE)
                .targetLanguage(TARGET_LANGUAGE)
                .build();

        assertThat(profile.generate(TRANSLATE_TEXT, GenerateAction.translate, attributes))
                .isNotBlank()
                .containsIgnoringCase(EXPECTED_TRANSLATION);
    }

    /**
     * Test: Call generic GENERATE with a Java-accepted but database-invalid target language.
     * Expected: DBMS_CLOUD_AI validation is surfaced as SelectAIException with the original SQL
     * error as its cause.
     */
    @Test
    void test16017GenerateTranslateSurfacesDatabaseValidationError() throws Exception {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .sourceLanguage(SOURCE_LANGUAGE)
                .targetLanguage("not-a-real-language")
                .build();

        assertThatThrownBy(() -> profile.generate(
                TRANSLATE_TEXT, GenerateAction.translate, attributes))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                    assertThat(exception.getCause().getMessage())
                            .contains("Invalid language or language code");
                });
    }





    /**
     * Test: Build a syntactically valid object-list value containing one generated, nonexistent
     * object name ({@code JSAI_MISSING_OBJECT_<UUID>}), set it on the created profile with
     * {@code setAttribute("object_list", invalidObjectList)}, verify the getter returns the
     * same value, and call {@code generate} with prompt
     * {@code "How many gymnasts in database?"} and action {@code GenerateAction.runsql}.
     * Expected: The database accepts the object-list setter and the getter returns the exact
     * supplied JSON/object-list value. Generation returns a non-success explanatory response
     * containing {@code "a valid SELECT statement could not be generated"}; the test does not
     * require a particular ORA code. Inherited cleanup removes the profile and temporary
     * credential.
     */
    @Test
    void test16018InvalidObjectListReportsDatabaseError() throws Exception {
        String missingObject = "JSAI_MISSING_OBJECT_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String invalidObjectList = objectListFor(missingObject);

        assertThat(profile.setAttribute("object_list", invalidObjectList)).isTrue();
        assertThat(profile.getProfileAttributes().getObjectList()).isEqualTo(invalidObjectList);

        assertThat(profile.generate(PROMPTS[0], GenerateAction.runsql))
                .contains("a valid SELECT statement could not be generated");
    }

    /**
     * Test: Calls the GENERATE showprompt action with a prompt larger than the PL/SQL VARCHAR2
     * bind limit.
     * Expected: The complete CLOB prompt reaches DBMS_CLOUD_AI without truncation.
     */
     @Test
    void test16019GenerateWithLargeClobPrompt() throws Exception {
        String prompt = largeClobValue("generate-prompt");

        assertThat(profile.generate(prompt, GenerateAction.showprompt))
                .isNotBlank()
                .contains("generate-prompt");
    }

    private static String largeClobValue(String marker) {
        return marker + ":" + "x".repeat(40_000 - marker.length() - 1);
    }
}
