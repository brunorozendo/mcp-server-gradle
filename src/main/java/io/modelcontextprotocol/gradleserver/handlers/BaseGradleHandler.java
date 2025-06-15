package io.modelcontextprotocol.gradleserver.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.gradleserver.GradleConnectionManager;
import io.modelcontextprotocol.spec.McpSchema;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ProjectConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for all Gradle tool handlers.
 * Provides common functionality for Gradle operations.
 */
public abstract class BaseGradleHandler {
    protected static final Logger logger = LoggerFactory.getLogger(BaseGradleHandler.class);
    protected static final ObjectMapper objectMapper = new ObjectMapper();
    
    protected final GradleConnectionManager connectionManager = GradleConnectionManager.getInstance();
    
    /**
     * Get the JSON schema for this tool.
     */
    public abstract McpSchema.JsonSchema getSchema();
    
    /**
     * Execute a Gradle build with the specified tasks and arguments.
     */
    protected BuildResult executeBuild(String projectPath, List<String> tasks, List<String> arguments) {
        validateProjectPath(projectPath);
        
        ProjectConnection connection = connectionManager.getConnection(projectPath);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        
        try {
            BuildLauncher launcher = connection.newBuild();
            
            // Set tasks
            if (tasks != null && !tasks.isEmpty()) {
                launcher.forTasks(tasks.toArray(new String[0]));
            }
            
            // Set arguments
            List<String> allArguments = new ArrayList<>();
            if (arguments != null) {
                allArguments.addAll(arguments);
            }
            
            // Add default arguments for better output
            if (!allArguments.contains("--console")) {
                allArguments.add("--console=plain");
            }
            
            if (!allArguments.isEmpty()) {
                launcher.withArguments(allArguments);
            }
            
            // Capture output
            launcher.setStandardOutput(outputStream);
            launcher.setStandardError(errorStream);
            
            // Execute build
            long startTime = System.currentTimeMillis();
            launcher.run();
            long duration = System.currentTimeMillis() - startTime;
            
            String output = outputStream.toString();
            String errors = errorStream.toString();
            
            return new BuildResult(true, output, errors, duration);
            
        } catch (Exception e) {
            String output = outputStream.toString();
            String errors = errorStream.toString();
            String errorMessage = e.getMessage();
            
            if (errors.isEmpty() && errorMessage != null) {
                errors = errorMessage;
            }
            
            logger.error("Build failed for project: {}", projectPath, e);
            return new BuildResult(false, output, errors, 0);
        }
    }
    
    /**
     * Validate that the project path exists and contains Gradle files.
     */
    protected void validateProjectPath(String projectPath) {
        if (projectPath == null || projectPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Project path cannot be null or empty");
        }
        
        File projectDir = new File(projectPath);
        if (!projectDir.exists()) {
            throw new IllegalArgumentException("Project directory does not exist: " + projectPath);
        }
        
        if (!projectDir.isDirectory()) {
            throw new IllegalArgumentException("Project path is not a directory: " + projectPath);
        }
    }
    
    /**
     * Create a standard tool result for successful operations.
     */
    protected McpSchema.CallToolResult createSuccessResult(String content) {
        return McpSchema.CallToolResult.builder()
            .addTextContent(content)
            .isError(false)
            .build();
    }
    
    /**
     * Create a standard tool result for failed operations.
     */
    protected McpSchema.CallToolResult createErrorResult(String error) {
        return McpSchema.CallToolResult.builder()
            .addTextContent(error)
            .isError(true)
            .build();
    }
    
    /**
     * Result of a Gradle build execution.
     */
    protected static class BuildResult {
        public final boolean success;
        public final String output;
        public final String errors;
        public final long durationMs;
        
        public BuildResult(boolean success, String output, String errors, long durationMs) {
            this.success = success;
            this.output = output;
            this.errors = errors;
            this.durationMs = durationMs;
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            
            if (success) {
                sb.append("BUILD SUCCESSFUL\n");
                if (durationMs > 0) {
                    sb.append(String.format("Total time: %.2f seconds\n", durationMs / 1000.0));
                }
            } else {
                sb.append("BUILD FAILED\n");
            }
            
            if (!output.isEmpty()) {
                sb.append("\nOutput:\n").append(output);
            }
            
            if (!errors.isEmpty()) {
                sb.append("\nErrors:\n").append(errors);
            }
            
            return sb.toString();
        }
    }
}
