/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.lifecycle;

import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.ToolStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Live coverage for DBMS_CLOUD_AI_AGENT task operations. */
class TaskIT extends AgentIntegrationFixture {
    /**
     * Test: Create an enabled task with the fixture task attributes and a description.
     * Expected: The handle exposes a nonblank name, an instruction containing {query}, and ENABLED status.
     */
    @Test
    void test31000TaskCreation() throws Exception {
        Task task = agentClient.task(uniqueName("TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        assertThat(task.getTaskName()).isNotBlank();
        assertThat(task.getTaskAttributes().getInstruction()).contains("{query}");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ENABLED);
    }

    /**
     * Test: Create a task and query both the full collection and an anchored name pattern.
     * Expected: The unfiltered collection includes the task, and the anchored pattern returns exactly that
     * task.
     */
    @Test
    void test31001ListTasks() throws Exception {
        Task task = agentClient.task(uniqueName("LIST_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        assertThat(agentClient.listTasks()).extracting(Task::getTaskName).contains(task.getTaskName());
        assertThat(agentClient.listTasks("^" + task.getTaskName() + "$"))
                .extracting(Task::getTaskName).containsExactly(task.getTaskName());
    }

    /**
     * Test: Create a disabled task with a description, instruction, and human-tool flag, then retrieve
     * it through a name-filtered list.
     * Expected: The single listed handle preserves its name, description, disabled status, instruction, and
     * enabled human-tool flag.
     */
    @Test
    void test31026ListTasksHydratesMetadataAndAttributes() throws Exception {
        String name = uniqueName("LIST_METADATA");
        TaskAttributes attributes = TaskAttributes.builder()
                .instruction("Answer this list metadata question: {query}")
                .enableHumanTool(true)
                .build();
        Task task = agentClient.task(name, attributes, "Task list metadata", TaskStatus.DISABLED);
        task.create();
        managedTasks.add(task);

        List<Task> listedTasks = agentClient.listTasks("^" + name + "$");
        assertThat(listedTasks).hasSize(1);
        Task listed = listedTasks.get(0);
        assertThat(listed.getTaskName()).isEqualTo(name);
        assertThat(listed.getDescription()).isEqualTo("Task list metadata");
        assertThat(listed.getStatus()).isEqualTo(TaskStatus.DISABLED);
        assertThat(listed.getTaskAttributes().getInstruction()).isEqualTo(attributes.getInstruction());
        assertThat(listed.getTaskAttributes().getEnableHumanTool()).isTrue();
    }

    /**
     * Test: Create five tasks sharing a unique prefix plus one task with a different prefix, then list
     * by the shared prefix.
     * Expected: The result contains exactly the five matching names, regardless of their order.
     */
    @Test
    void test31027ListTasksMultipleMatchesUsingPattern() throws Exception {
        List<String> names = createTaskListFixture();
        String prefix = listPrefix(names.get(0));

        assertThat(listTaskNames("^" + prefix)).containsExactlyInAnyOrderElementsOf(names.subList(0, 5));
    }

    /**
     * Test: Create tasks with a mixed-case unique prefix and search using its lowercase form.
     * Expected: The pattern matches all five tasks with that prefix and excludes the differently prefixed
     * task.
     */
    @Test
    void test31028ListTasksCaseInsensitivePattern() throws Exception {
        List<String> names = createTaskListFixture();
        String prefix = listPrefix(names.get(0));

        assertThat(listTaskNames("^" + prefix.toLowerCase(Locale.ROOT)))
                .containsExactlyInAnyOrderElementsOf(names.subList(0, 5));
    }

    /**
     * Test: Create two groups of tasks with distinct prefixes and search with a pattern that alternates
     * between them.
     * Expected: The result contains exactly all six names from both groups.
     */
    @Test
    void test31029ListTasksAlternationPattern() throws Exception {
        List<String> names = createTaskListFixture();
        String firstPrefix = listPrefix(names.get(0));
        String secondPrefix = listPrefix(names.get(5));

        assertThat(listTaskNames("^(" + firstPrefix + "|" + secondPrefix + ")"))
                .containsExactlyInAnyOrderElementsOf(names);
    }

    /**
     * Test: Search for an anchored pattern built from a unique prefix that no task uses.
     * Expected: The task collection is empty.
     */
    @Test
    void test31030ListTasksNonMatchingPatternReturnsEmptyList() throws Exception {
        String pattern = "^" + uniqueName("NO_MATCH") + "$";

        assertThat(agentClient.listTasks(pattern)).isEmpty();
    }

    /**
     * Test: Search for a unique literal name that does not identify a task.
     * Expected: The task collection is empty.
     */
    @Test
    void test31031ListTasksLiteralNonMatchingPatternReturnsEmptyList() throws Exception {
        assertThat(agentClient.listTasks(uniqueName("NO_MATCH_LITERAL"))).isEmpty();
    }

    /**
     * Test: Create the list fixture and search using the first task name followed by an end anchor.
     * Expected: Only the selected task is returned.
     */
    @Test
    void test31032ListTasksEndAnchorPattern() throws Exception {
        List<String> names = createTaskListFixture();
        String exact = names.get(0);
        String pattern = exact + "$";

        assertThat(listTaskNames(pattern)).containsExactly(exact);
    }

    /**
     * Test: Create a task, then search with a 1,000-character regular-expression body.
     * Expected: The call throws SelectAIException and its database cause contains ORA-12733.
     */
    @Test
    void test31033ListTasksOverlongPatternIsRejectedByDatabase() throws Exception {
        Task task = agentClient.task(uniqueName("OVERLONG_REGEX_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String pattern = "^" + "a".repeat(1000) + "$";

        assertThatThrownBy(() -> agentClient.listTasks(pattern))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12733:"));
    }

    /**
     * Test: Create three tasks in B, A, C order and list them with a pattern matching their shared
     * prefix.
     * Expected: The returned names are ordered A, B, C.
     */
    @Test
    void test31034ListTasksAreOrderedByName() throws Exception {
        String prefix = uniqueName("LIST_ORDER");
        String first = prefix + "_A";
        String second = prefix + "_B";
        String third = prefix + "_C";
        Task secondTask = agentClient.task(second, taskAttributes(), "Java task test", TaskStatus.ENABLED);
        secondTask.create();
        managedTasks.add(secondTask);
        Task firstTask = agentClient.task(first, taskAttributes(), "Java task test", TaskStatus.ENABLED);
        firstTask.create();
        managedTasks.add(firstTask);
        Task thirdTask = agentClient.task(third, taskAttributes(), "Java task test", TaskStatus.ENABLED);
        thirdTask.create();
        managedTasks.add(thirdTask);

        assertThat(listTaskNames("^" + prefix + "_[ABC]$"))
                .containsExactly(first, second, third);
    }

    /**
     * Test: Create a task and load a second handle using its name.
     * Expected: The fetched handle has the same name and instruction as the created task.
     */
    @Test
    void test31002FetchTask() throws Exception {
        Task created = agentClient.task(uniqueName("FETCH_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        created.create();
        managedTasks.add(created);
        Task fetched = agentClient.task(created.getTaskName());
        assertThat(fetched.getTaskName()).isEqualTo(created.getTaskName());
        assertThat(fetched.getTaskAttributes().getInstruction())
                .isEqualTo(created.getTaskAttributes().getInstruction());
    }

    /**
     * Test: Create a task without specifying its description or status.
     * Expected: The created task reports ENABLED status.
     */
    @Test
    void test31003DefaultStatusEnabled() throws Exception {
        String name = uniqueName("DEFAULT_TASK");
        Task task = agentClient.task(name, taskAttributes(), null, null);
        task.create();
        managedTasks.add(task);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ENABLED);
    }


    /**
     * Test: Create an enabled task, then disable and re-enable it.
     * Expected: Both status changes succeed, and the handle reports DISABLED then ENABLED.
     */
    @Test
    void test31004DisableEnableTask() throws Exception {
        Task task = agentClient.task(uniqueName("STATUS_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        assertThat(task.disable()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DISABLED);
        assertThat(task.enable()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ENABLED);
    }

    /**
     * Test: Create an enabled task and request that it be enabled again.
     * Expected: The call throws SelectAIException with ORA-20051 in its cause, and status remains ENABLED.
     */
    @Test
    void test31005EnableAlreadyEnabledTaskFails() throws Exception {
        Task task = agentClient.task(uniqueName("ENABLE_TWICE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ENABLED);

        assertThatThrownBy(task::enable)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).hasMessageContaining("ORA-20051:"));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ENABLED);
    }

    /**
     * Test: Create a disabled task and request that it be disabled again.
     * Expected: The call throws SelectAIException with ORA-20051 in its cause, and status remains DISABLED.
     */
    @Test
    void test31006DisableAlreadyDisabledTaskFails() throws Exception {
        Task task = agentClient.task(uniqueName("DISABLE_TWICE_TASK"), taskAttributes(), null,
                TaskStatus.DISABLED);
        task.create();
        managedTasks.add(task);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DISABLED);

        assertThatThrownBy(task::disable)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).hasMessageContaining("ORA-20051:"));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DISABLED);
    }

    /**
     * Test: Create a disabled task configured with an instruction, SQL tool, input task, and human-tool
     * flag, then fetch it.
     * Expected: The fetched task preserves its name, description, disabled status, and each configured
     * attribute.
     */
    @Test
    void test31007AllTaskAttributesRoundTrip() throws Exception {
        Tool tool = agentClient.tool(uniqueName("ROUND_TRIP_TASK_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task parent = agentClient.task(uniqueName("ROUND_TRIP_PARENT_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        parent.create();
        managedTasks.add(parent);
        TaskAttributes attributes = TaskAttributes.builder()
                .instruction("Use the configured tool to answer: {query}")
                .tools(List.of(tool.getToolName()))
                .input(List.of(parent.getTaskName()))
                .enableHumanTool(true)
                .build();
        String name = uniqueName("ALL_TASK_ATTRIBUTES");
        Task created = agentClient.task(name, attributes, "All attributes task", TaskStatus.DISABLED);
        created.create();
        managedTasks.add(created);

        Task fetched = agentClient.task(name);
        TaskAttributes fetchedAttributes = fetched.getTaskAttributes();
        assertThat(fetched.getTaskName()).isEqualTo(name);
        assertThat(fetched.getDescription()).isEqualTo("All attributes task");
        assertThat(fetched.getStatus()).isEqualTo(TaskStatus.DISABLED);
        assertThat(fetchedAttributes.getInstruction()).isEqualTo(attributes.getInstruction());
        assertThat(fetchedAttributes.getTools()).containsExactlyElementsOf(attributes.getTools());
        assertThat(fetchedAttributes.getInput()).containsExactlyElementsOf(attributes.getInput());
        assertThat(fetchedAttributes.getEnableHumanTool())
                .isEqualTo(attributes.getEnableHumanTool());
    }

    /**
     * Test: Create a parent task and a child task whose input references the parent.
     * Expected: The child task reports exactly the parent task name as its input.
     */
    @Test
    void test31008TaskInputChaining() throws Exception {
        Task parent = agentClient.task(uniqueName("PARENT_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        parent.create();
        managedTasks.add(parent);
        String childName = uniqueName("CHILD_TASK");
        TaskAttributes childAttributes = TaskAttributes.builder()
                .instruction("Summarize the upstream task result")
                .input(List.of(parent.getTaskName()))
                .build();
        Task child = agentClient.task(childName, childAttributes, null, TaskStatus.ENABLED);
        child.create();
        managedTasks.add(child);
        assertThat(child.getTaskAttributes().getInput()).containsExactly(parent.getTaskName());
    }

    /**
     * Test: Create a task with the human-tool attribute enabled.
     * Expected: The created task reports the human-tool attribute as true.
     */
    @Test
    void test31009HumanToolAttribute() throws Exception {
        TaskAttributes attributes = TaskAttributes.builder()
                .instruction("Request human confirmation when needed")
                .enableHumanTool(true)
                .build();
        String name = uniqueName("HUMAN_TASK");
        Task task = agentClient.task(name, attributes, null, TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        assertThat(task.getTaskAttributes().getEnableHumanTool()).isTrue();
    }

    /**
     * Test: Ask the client to construct a task handle with an empty name.
     * Expected: Construction throws IllegalArgumentException.
     */
    @Test
    void test31010MissingTaskNameFailsLocally() {
        assertThatThrownBy(() -> agentClient.task("", taskAttributes(), null, TaskStatus.ENABLED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Ask the client to construct a task handle without attributes.
     * Expected: Construction throws IllegalArgumentException.
     */
    @Test
    void test31011MissingTaskAttributesFailsLocally() {
        assertThatThrownBy(() -> agentClient.task(uniqueName("NO_ATTRIBUTES"), null, null,
                TaskStatus.ENABLED)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Create and force-drop a task, then try to enable its deleted handle.
     * Expected: The status change throws an exception.
     */
    @Test
    void test31012EnableDeletedTaskFails() throws Exception {
        String name = uniqueName("DELETED_TASK");
        Task task = agentClient.task(name, taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        assertThat(task.drop(true)).isTrue();
        managedTasks.remove(task);
        assertThatThrownBy(task::enable).isInstanceOf(Exception.class);
    }

    /**
     * Test: Create and force-drop a task, then try to disable its deleted handle.
     * Expected: The status change throws an exception.
     */
    @Test
    void test31020DisableDeletedTaskFails() throws Exception {
        Task task = agentClient.task(uniqueName("DELETED_DISABLE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        assertThat(task.drop(true)).isTrue();
        managedTasks.remove(task);
        assertThatThrownBy(task::disable).isInstanceOf(Exception.class);
    }

    /**
     * Test: Create a task, then attempt to create a second handle with the same name.
     * Expected: The duplicate creation throws an exception, while the original task retains its name.
     */
    @Test
    void test31013DuplicateTaskCreationFails() throws Exception {
        String name = uniqueName("DUPLICATE_TASK");
        Task first = agentClient.task(name, taskAttributes(), "Java task test", TaskStatus.ENABLED);
        first.create();
        managedTasks.add(first);
        Task duplicate = agentClient.task(name, taskAttributes(), null, TaskStatus.ENABLED);
        assertThatThrownBy(duplicate::create).isInstanceOf(Exception.class);
        assertThat(first.getTaskName()).isEqualTo(name);
    }

    /**
     * Test: Create a task, then list tasks using an unclosed regular-expression character class.
     * Expected: The call throws SelectAIException and its database cause contains ORA-12726.
     */
    @Test
    void test31014InvalidTaskRegexFails() throws Exception {
        Task task = agentClient.task(uniqueName("INVALID_REGEX_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);

        assertThatThrownBy(() -> agentClient.listTasks("[unclosed"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12726:"));
    }

    /**
     * Test: Create a disabled task and drop it with force disabled, then look it up by name.
     * Expected: The drop succeeds, and the subsequent lookup throws an exception.
     */
    @Test
    void test31015DeleteDisabledTaskWithoutForce() throws Exception {
        String name = uniqueName("DISABLED_DELETE");
        Task task = agentClient.task(name, taskAttributes(), null, TaskStatus.DISABLED);
        task.create();
        managedTasks.add(task);
        assertThat(task.drop(false)).isTrue();
        managedTasks.remove(task);
        assertThatThrownBy(() -> agentClient.task(name)).isInstanceOf(Exception.class);
    }

    /**
     * Test: Attempt to create a task whose instruction is an empty string.
     * Expected: Creation throws SelectAIException with a nonnull error code and a SQL cause containing an
     * ORA error.
     */
    @Test
    void test31016EmptyInstructionRejectedByDatabase() throws Exception {
        Task task = agentClient.task(uniqueName("NO_INSTRUCTION"),
                TaskAttributes.builder().instruction("").build(), null, TaskStatus.ENABLED);
        assertThatThrownBy(task::create)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isNotNull();
                            assertThat(exception.getCause()).isInstanceOf(java.sql.SQLException.class)
                                    .hasMessageContaining("ORA-");
                        });
    }

    /**
     * Test: Attempt to create a task without an instruction attribute.
     * Expected: Creation throws SelectAIException with a nonnull error code and a SQL cause containing an
     * ORA error.
     */
    @Test
    void test31018MissingInstructionRejectedByDatabase() throws Exception {
        Task task = agentClient.task(uniqueName("MISSING_INSTRUCTION"),
                TaskAttributes.builder().build(), null, TaskStatus.ENABLED);
        assertThatThrownBy(task::create)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isNotNull();
                            assertThat(exception.getCause()).isInstanceOf(java.sql.SQLException.class)
                                    .hasMessageContaining("ORA-");
                        });
    }

    /**
     * Test: Create an enabled task and drop it with force disabled, then look it up by name.
     * Expected: The drop succeeds, and the subsequent lookup throws an exception.
     */
    @Test
    void test31017DeleteEnabledTaskWithoutForce() throws Exception {
        String name = uniqueName("ENABLED_DELETE");
        Task task = agentClient.task(name, taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        assertThat(task.drop(false)).isTrue();
        managedTasks.remove(task);
        assertThatThrownBy(() -> agentClient.task(name)).isInstanceOf(Exception.class);
    }

    /**
     * Test: Create a task, force-drop it, and create it again through the same handle.
     * Expected: The first lookup fails after the drop, recreation succeeds, and a fresh lookup returns the
     * name.
     */
    @Test
    void test31021DeleteAndRecreateTask() throws Exception {
        String name = uniqueName("RECREATE_TASK");
        Task task = agentClient.task(name, taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        assertThat(task.drop(true)).isTrue();
        managedTasks.remove(task);
        assertThatThrownBy(() -> agentClient.task(name)).isInstanceOf(Exception.class);
        assertThat(task.create()).isTrue();
        managedTasks.add(task);
        assertThat(agentClient.task(name).getTaskName()).isEqualTo(name);
    }

    /**
     * Test: Create and drop a task with force disabled, then look it up by name.
     * Expected: The drop succeeds, and the subsequent lookup reports that the task was not found.
     */
    @Test
    void test31022DropExistingTaskWithoutForce() throws Exception {
        String name = uniqueName("DROP_NON_FORCE_TASK");
        Task task = agentClient.task(name, taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        assertThat(task.drop(false)).isTrue();
        managedTasks.remove(task);
        assertThatThrownBy(() -> agentClient.task(name))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI task not found: " + name);
    }

    /**
     * Test: Create a task and force-drop the same handle twice.
     * Expected: Both drop calls return true.
     *
     * Bug <a href="https://bug.oraclecorp.com/ords/bug/bugui/bug-details?mode=edit&bugnumber=40126447">
     * 40126447</a> - DBMS_CLOUD_AI_AGENT Java drop APIs must not reject repeated forced drops locally.
     */
    @Test
    void test31023RepeatedForceDropOnSameTaskHandleSucceeds() throws Exception {
        Task task = agentClient.task(uniqueName("DROP_REPEAT_FORCE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        assertThat(task.drop(true)).isTrue();
        managedTasks.remove(task);
        assertThat(task.drop(true)).isTrue();
    }

    /**
     * Test: Create two handles for one task, then force-drop the task through each handle.
     * Expected: The first and repeated force-drop calls both return true.
     */
    @Test
    void test31024ForceDropAlreadyMissingTaskSucceeds() throws Exception {
        String name = uniqueName("DROP_MISSING_FORCE_TASK");
        Task first = agentClient.task(name, taskAttributes(), "Java task test", TaskStatus.ENABLED);
        first.create();
        managedTasks.add(first);
        Task second = agentClient.task(name);

        assertThat(first.drop(true)).isTrue();
        managedTasks.remove(first);
        assertThat(second.drop(true)).isTrue();
    }

    /**
     * Test: Create two handles for one task, force-drop it through the first, then drop it without
     * force through the second.
     * Expected: The second call throws SelectAIException with a database error code and a cause naming the
     * missing task.
     */
    @Test
    void test31025NonForceDropMissingTaskPreservesDatabaseError() throws Exception {
        String name = uniqueName("DROP_MISSING_NON_FORCE_TASK");
        Task first = agentClient.task(name, taskAttributes(), "Java task test", TaskStatus.ENABLED);
        first.create();
        managedTasks.add(first);
        Task second = agentClient.task(name);

        assertThat(first.drop(true)).isTrue();
        managedTasks.remove(first);
        assertThatThrownBy(() -> second.drop(false))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isNotNull();
                            assertThat(exception.getCause()).hasMessageContaining("ORA-")
                                    .hasMessageContaining(name)
                                    .hasMessageContaining("does not exist");
                        });
    }

    /**
     * Test: Create a task, retrieve it from a filtered list, and force-drop the listed handle.
     * Expected: The listed collection contains one task, the drop succeeds, and a later filtered list is
     * empty.
     */
    @Test
    void test31035DropTaskLoadedFromList() throws Exception {
        Task source = agentClient.task(uniqueName("DROP_LISTED_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        source.create();
        managedTasks.add(source);
        String name = source.getTaskName();

        List<Task> listedTasks = agentClient.listTasks("^" + name + "$");
        assertThat(listedTasks).hasSize(1);
        assertThat(listedTasks.get(0).drop(true)).isTrue();
        managedTasks.remove(source);

        assertThat(listTaskNames("^" + name + "$")).isEmpty();
    }

    /**
     * Test: Create two handles for one task, drop it through the source handle, then read status and
     * attributes from the other handle.
     * Expected: Both reads throw SelectAIException stating that the task was not found.
     */
    @Test
    void test31036StaleTaskStatusAndAttributesFailAfterExternalDrop() throws Exception {
        Task source = agentClient.task(uniqueName("STALE_DROP_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        source.create();
        managedTasks.add(source);
        String name = source.getTaskName();
        Task stale = agentClient.task(name);

        assertThat(source.drop(true)).isTrue();
        managedTasks.remove(source);

        assertThatThrownBy(stale::getStatus)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI task not found: " + name);
        assertThatThrownBy(stale::getTaskAttributes)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI task not found: " + name);
    }

    /**
     * Test: Force-drop a handle for a task that was never created, then try a non-force drop on another
     * uncreated handle.
     * Expected: The force drop returns true, while the non-force drop throws IllegalStateException.
     */
    @Test
    void test31037NeverCreatedTaskForceDropDelegatesToDatabase() throws Exception {
        String forceName = uniqueName("UNCREATED_FORCE_DROP");
        Task forceHandle = agentClient.task(forceName, taskAttributes(), null, TaskStatus.ENABLED);
        assertThat(forceHandle.drop(true)).isTrue();

        Task nonForceHandle = agentClient.task(uniqueName("UNCREATED_NON_FORCE_DROP"),
                taskAttributes(), null, TaskStatus.ENABLED);
        assertThatThrownBy(() -> nonForceHandle.drop(false))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Test: Create a task, drop it without force, then repeat the non-force drop on the same handle.
     * Expected: The first drop succeeds, the second throws IllegalStateException, and listing the name
     * returns no tasks.
     */
    @Test
    void test31038RepeatedNonForceDropOnSameTaskHandleIsRejectedLocally() throws Exception {
        Task task = agentClient.task(uniqueName("DROP_REPEAT_NON_FORCE"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String name = task.getTaskName();

        assertThat(task.drop(false)).isTrue();
        managedTasks.remove(task);
        assertThatThrownBy(() -> task.drop(false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(listTaskNames("^" + name + "$")).isEmpty();
    }

    private List<String> createTaskListFixture() throws Exception {
        String listPrefix = uniqueName("LIST_PATTERN");
        String otherPrefix = uniqueName("LIST_OTHER");
        List<String> names = List.of(
                listPrefix + "_1",
                listPrefix + "_2",
                listPrefix + "_3",
                listPrefix + "_4",
                listPrefix + "_5",
                otherPrefix + "_1");
        for (String name : names) {
            Task task = agentClient.task(name, taskAttributes(), "Java task test", TaskStatus.ENABLED);
            task.create();
            managedTasks.add(task);
        }
        return names;
    }

    private List<String> listTaskNames(String pattern) throws Exception {
        return agentClient.listTasks(pattern).stream().map(Task::getTaskName).toList();
    }

    private String listPrefix(String name) {
        return name.substring(0, name.lastIndexOf('_'));
    }
}
