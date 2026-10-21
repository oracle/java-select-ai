/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.model.agent.RunTeamParams;
import com.oracle.database.selectai.model.agent.RunTeamRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Builds precise runtime variables and reuses a team-run request builder.
 * No database connection or environment variables are required. This sample
 * only serializes requests; use RunTeamSample to submit a request with a real
 * conversation identifier.
 *
 * @see RunTeamSample
 * @see AgentWorkflowSample
 */
public final class RunTeamParamsSample {
    private RunTeamParamsSample() {
    }

    /**
     * Prints typed parameters and independent requests built from one builder.
     *
     * @param args command-line arguments; not used
     */
    public static void main(String[] args) {
        BigDecimal amount = new BigDecimal("1.234567890123456789");
        Map<String, Object> values = Map.of("amount", amount,
                "items", List.of(Map.of("amount", amount)));
        RunTeamParams params = RunTeamParams.builder()
                .instructionVariables(values)
                .attributeVariables(values)
                .stateVariables(values)
                .build();
        System.out.println("Precise runtime parameters: " + params.toParamsJson());

        RunTeamRequest.Builder builder = RunTeamRequest.builder()
                .stateVariables(values)
                .userPrompt("Review the amount.");
        RunTeamRequest first = builder.build();
        RunTeamRequest next = builder.userPrompt("Explain the calculation.").build();
        System.out.println("First prompt: " + first.getUserPrompt());
        System.out.println("Next prompt: " + next.getUserPrompt());
    }
}
