/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs capture checks with real DEBUG/INFO/ERROR output, isolated from quiet test logging. */
class AgentLoggingTest {
    @TempDir
    Path outputDirectory;

    @ParameterizedTest
    @ValueSource(strings = {"payloads", "benign-failures", "exception-content", "response-objects"})
    void capturesEnabledAgentLogs(String scenario) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path output = outputDirectory.resolve(scenario + ".log");
        ProcessBuilder builder = new ProcessBuilder(java,
                "-Dorg.slf4j.simpleLogger.log.com.oracle.database.selectai.impl.agent=debug",
                "-Dorg.slf4j.simpleLogger.cacheOutputStream=false",
                "-Dorg.slf4j.simpleLogger.logFile=System.err",
                "-cp", classPath, AgentLoggingProbe.class.getName(), scenario)
                .redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().keySet().removeIf(name -> name.startsWith("SELECTAI_")
                || List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS").contains(name));
        Process process = builder.start();
        try {
            assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("Logging probe completed").isTrue();
            String result = Files.readString(output);
            assertThat(process.exitValue()).withFailMessage("Logging probe failed: %s", result).isZero();
            assertThat(result).contains("Verified log capture: " + scenario);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }
}
