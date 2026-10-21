/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

/**
 * Configuration, request, response, status, and discovery models for Oracle
 * Select AI Agent operations.
 * <p>
 * The models in this package are used with the public agent interfaces and
 * represent data exchanged with the {@code DBMS_CLOUD_AI_AGENT} PL/SQL
 * package. Builders prepare values locally; constructing a model does not
 * execute a database operation. Built models retain immutable snapshots of
 * their configured collections and maps; builders are mutable and should not
 * be shared across threads without synchronization.
 *
 * <h2>Resource and tool configuration</h2>
 * {@link AgentAttributes}, {@link TaskAttributes}, {@link ToolAttributes}, and
 * {@link TeamAttributes} describe creation settings, attribute updates, and
 * attributes loaded from database metadata. {@link AgentStatus},
 * {@link TaskStatus}, {@link ToolStatus}, and {@link TeamStatus} describe
 * resource enablement, not workflow execution state.
 * <p>
 * {@link ToolParams} provides immutable built-in tool configuration through
 * {@link SqlToolParams}, {@link RagToolParams}, {@link WebSearchToolParams},
 * {@link SlackNotificationToolParams}, and
 * {@link EmailNotificationToolParams}. {@link GenericToolParams} preserves
 * parameters for other database-defined tool types or schemas that cannot be
 * represented by the known models. Known tool-parameter models also retain
 * additional database fields. {@link ToolType} is an extensible value object
 * that preserves tool-type values introduced by newer database releases.
 * {@link ToolInput} models input names and descriptions in the immutable
 * {@code tool_inputs} list exposed by {@link ToolAttributes}. Descriptions
 * guide the model and do not enforce supported input values in Java.
 *
 * <h2>Team requests and results</h2>
 * <ul>
 *   <li>{@link RunTeamRequest} carries the prompt and {@link RunTeamParams}
 *       for a team run. Runtime parameters include a conversation ID and
 *       instruction, attribute, and state variable maps.</li>
 *   <li>{@link ExecutionStateRequest} carries the requested
 *       {@link TeamExecutionState} and {@link ExecutionStateParams} identifying
 *       the conversation. An explicit non-blank conversation ID is required.</li>
 *   <li>{@link ExportTeamParams} models export options.
 *       {@link ExportTeamRequest} adds a destination and optional credential;
 *       {@link ImportTeamRequest} accepts a location or inline specification.</li>
 *   <li>{@link TeamRunResult} and {@link ToolRunResult} retain raw database
 *       output, which can be text or JSON, without imposing an output schema.</li>
 *   <li>{@link TeamExecutionState} represents workflow state. Unknown non-blank
 *       database states map to {@link TeamExecutionState#UNKNOWN}; that sentinel
 *       cannot be submitted in an execution-state update.</li>
 *   <li>{@link TeamDescription} exposes team metadata and retains the skills
 *       representation as JSON text. {@link AvailableTeam} is a read-only
 *       discovery snapshot, not a mutable team handle.</li>
 * </ul>
 * Not every database response has a dedicated model: tool descriptions and
 * exported definitions remain JSON strings. Exported definitions may have an
 * object or array as their top-level JSON value.
 *
 * <h2>Validation</h2>
 * Resource-attribute builders do not require fields that the database
 * validates when a resource is created. They retain documented Java checks,
 * such as numeric ranges and collection structure. Request models validate
 * required arguments and mutually exclusive input forms where documented.
 * The database applies semantic validation and version-specific behavior when
 * operations are executed. See each model's contract for omitted values and
 * validation rules; typed requests do not imply unrestricted raw-JSON support.
 *
 * @see com.oracle.database.selectai.agent
 * @see com.oracle.database.selectai.agent.SelectAIAgent
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
 *      ToolInputSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
 *      ToolParamsSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/AgentWorkflowSample.html">
 *      AgentWorkflowSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTeamExecutionStateSample.html">
 *      SetTeamExecutionStateSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ExportTeamSample.html">
 *      ExportTeamSample source</a>
 * @since 1.1.0
 */
package com.oracle.database.selectai.model.agent;
