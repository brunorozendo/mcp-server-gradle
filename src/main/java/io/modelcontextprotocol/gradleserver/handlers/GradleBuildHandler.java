package io.modelcontextprotocol.gradleserver.handlers;

import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Handles Gradle build operations.
 */
public class GradleBuildHandler extends BaseGradleHandler {
    
    @Override
    public McpSchema.JsonSchema getSchema() {
        Map<String, Object> properties = new HashMap<>();
        
        // Project path property
        Map<String, Object> projectPath = new HashMap<>();
        projectPath.put("type", "string");
        projectPath.put("description", "Absolute path to the Gradle project directory");
        properties.put("projectPath", projectPath);
        
        // Tasks property
        Map<String, Object> tasks = new HashMap<>();
        tasks.put("type", "array");
        Map<String, Object> items = new HashMap<>();
        items.put("type", "string");
        tasks.put("items", items);
        tasks.put("description", "List of Gradle tasks to execute (e.g., clean, build, test)");
        tasks.put("default", List.of("build"));
        properties.put("tasks", tasks);
        
        // Arguments property
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "array");
        arguments.put("items", items);
        arguments.put("description", "Additional Gradle arguments (e.g., --info, --stacktrace, -x test)");
        properties.put("arguments", arguments);
        
        // Environment property
        Map<String, Object> environment = new HashMap<>();
        environment.put("type", "object");
        environment.put("description", "Environment variables for the build");
        Map<String, Object> envProps = new HashMap<>();
        envProps.put("type", "string");
        environment.put("additionalProperties", envProps);
        properties.put("environment", environment);
        
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
                List<String> tasks = (List<String>) arguments.getOrDefault("tasks", List.of("build"));
                List<String> gradleArgs = (List<String>) arguments.get("arguments");
                Map<String, String> environment = (Map<String, String>) arguments.get("environment");
                
                logger.info("Executing Gradle build in: {} with tasks: {}", projectPath, tasks);
                
                // Set environment variables if provided
                if (environment != null && !environment.isEmpty()) {
                    environment.forEach((key, value) -> {
                        System.setProperty(key, value);
                    });
                }
                
                BuildResult result = executeBuild(projectPath, tasks, gradleArgs);
                
                if (result.success) {
                    return createSuccessResult(result.getSummary());
                } else {
                    return createErrorResult(result.getSummary());
                }
                
            } catch (Exception e) {
                logger.error("Error executing Gradle build", e);
                return createErrorResult("Build failed: " + e.getMessage());
            }
        });
    }
}
