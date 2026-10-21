/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolInput;

import java.util.List;

/**
 * Builds and reads typed tool input metadata without connecting to a database.
 * No environment variables are required. The printed JSON contains only
 * illustrative input metadata, not a live tool configuration or input values.
 * Descriptions guide the model; they do not enforce an allowed-value list.
 * The single-input and multiple-input examples are independent illustrations;
 * choose input names that match the function configured for a real tool.
 *
 * @see CreateToolSample
 * @see CreateToolWithInputsSample
 * @see SetToolInputsSample
 * @see LoadToolSample
 */
public final class ToolInputSample {
    private ToolInputSample() {
    }

    /**
     * Serializes input metadata and reads the immutable typed entries.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        ToolInput input = ToolInput.builder()
                .name("data_guard")
                .description("Only supported values are \"Enabled\" and \"Disabled\".")
                .build();
        ToolAttributes attributes = ToolAttributes.builder()
                .toolInputs(List.of(input))
                .build();

        System.out.println(attributes.toJson());
        for (ToolInput configuredInput : attributes.getToolInputs()) {
            System.out.printf("Input: %s%nDescription: %s%n",
                    configuredInput.getName(), configuredInput.getDescription());
        }

        ToolInput requestId = ToolInput.builder().name("request_id").build();
        ToolAttributes multipleInputs = ToolAttributes.builder()
                .toolInputs(List.of(input, requestId))
                .build();
        System.out.printf("Multiple inputs: %s%n", multipleInputs.toJson());
        System.out.printf("Optional description present: %s%n", requestId.getDescription() != null);
    }
}
