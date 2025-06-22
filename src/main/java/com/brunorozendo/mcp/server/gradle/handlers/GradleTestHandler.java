package com.brunorozendo.mcp.server.gradle.handlers;

import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles Gradle test execution with filtering and result parsing.
 */
public class GradleTestHandler extends BaseGradleHandler {
    
    private static final Pattern TEST_RESULT_PATTERN = Pattern.compile(
        "BUILD (SUCCESSFUL|FAILED) in \\d+[ms]"
    );
    
    private static final Pattern TEST_SUMMARY_PATTERN = Pattern.compile(
        "(\\d+) tests? completed(?:, (\\d+) failed)?(?:, (\\d+) skipped)?"
    );
    
    @Override
    public McpSchema.JsonSchema getSchema() {
        Map<String, Object> properties = new HashMap<>();
        
        // Project path property
        Map<String, Object> projectPath = new HashMap<>();
        projectPath.put("type", "string");
        projectPath.put("description", "Absolute path to the Gradle project directory");
        properties.put("projectPath", projectPath);
        
        // Test filter property
        Map<String, Object> testFilter = new HashMap<>();
        testFilter.put("type", "string");
        testFilter.put("description", "Test filter pattern (e.g., *IntegrationTest, com.example.MyTest.testMethod)");
        properties.put("testFilter", testFilter);
        
        // Include integration tests property
        Map<String, Object> includeIntegrationTests = new HashMap<>();
        includeIntegrationTests.put("type", "boolean");
        includeIntegrationTests.put("description", "Whether to include integration tests");
        includeIntegrationTests.put("default", false);
        properties.put("includeIntegrationTests", includeIntegrationTests);
        
        // Parallel execution property
        Map<String, Object> parallel = new HashMap<>();
        parallel.put("type", "boolean");
        parallel.put("description", "Run tests in parallel");
        parallel.put("default", false);
        properties.put("parallel", parallel);
        
        // Additional arguments property
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "array");
        Map<String, Object> items = new HashMap<>();
        items.put("type", "string");
        arguments.put("items", items);
        arguments.put("description", "Additional test arguments");
        properties.put("arguments", arguments);
        
        return new McpSchema.JsonSchema(
            "object",
            properties,
            List.of("projectPath"),
            false,
            null,
            null
        );
    }
    
    public Mono<McpSchema.CallToolResult> handle(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            try {
                String projectPath = (String) arguments.get("projectPath");
                String testFilter = (String) arguments.get("testFilter");
                Boolean includeIntegrationTests = (Boolean) arguments.getOrDefault("includeIntegrationTests", false);
                Boolean parallel = (Boolean) arguments.getOrDefault("parallel", false);
                List<String> additionalArgs = (List<String>) arguments.get("arguments");
                
                List<String> tasks = new ArrayList<>();
                tasks.add("test");
                
                if (includeIntegrationTests) {
                    tasks.add("integrationTest");
                }
                
                List<String> gradleArgs = new ArrayList<>();
                if (additionalArgs != null) {
                    gradleArgs.addAll(additionalArgs);
                }
                
                if (testFilter != null && !testFilter.isEmpty()) {
                    gradleArgs.add("--tests");
                    gradleArgs.add(testFilter);
                }
                
                if (parallel) {
                    gradleArgs.add("--parallel");
                }
                
                // Always add info for better test output
                if (!gradleArgs.contains("--info")) {
                    gradleArgs.add("--info");
                }
                
                logger.info("Executing tests in: {} with filter: {}", projectPath, testFilter);
                
                BuildResult result = executeBuild(projectPath, tasks, gradleArgs);
                
                // Parse test results
                TestResults testResults = parseTestResults(result.output);
                
                StringBuilder response = new StringBuilder();
                response.append(result.success ? "TEST EXECUTION SUCCESSFUL\n" : "TEST EXECUTION FAILED\n");
                response.append("\n");
                
                if (testResults != null) {
                    response.append("Test Summary:\n");
                    response.append("- Total: ").append(testResults.total).append("\n");
                    response.append("- Passed: ").append(testResults.passed).append("\n");
                    response.append("- Failed: ").append(testResults.failed).append("\n");
                    response.append("- Skipped: ").append(testResults.skipped).append("\n");
                    response.append("\n");
                }
                
                if (!result.output.isEmpty()) {
                    response.append("Output:\n").append(result.output);
                }
                
                if (!result.errors.isEmpty()) {
                    response.append("\nErrors:\n").append(result.errors);
                }
                
                return result.success ? 
                    createSuccessResult(response.toString()) : 
                    createErrorResult(response.toString());
                
            } catch (Exception e) {
                logger.error("Error executing tests", e);
                return createErrorResult("Test execution failed: " + e.getMessage());
            }
        });
    }
    
    private TestResults parseTestResults(String output) {
        try {
            Matcher matcher = TEST_SUMMARY_PATTERN.matcher(output);
            if (matcher.find()) {
                int total = Integer.parseInt(matcher.group(1));
                int failed = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
                int skipped = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
                int passed = total - failed - skipped;
                
                return new TestResults(total, passed, failed, skipped);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse test results", e);
        }
        return null;
    }
    
    private static class TestResults {
        final int total;
        final int passed;
        final int failed;
        final int skipped;
        
        TestResults(int total, int passed, int failed, int skipped) {
            this.total = total;
            this.passed = passed;
            this.failed = failed;
            this.skipped = skipped;
        }
    }
}
