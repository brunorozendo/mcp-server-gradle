package io.modelcontextprotocol.gradleserver.handlers;

import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import java.io.File;
import java.util.*;

/**
 * Handles Gradle clean operations.
 */
public class GradleCleanHandler extends BaseGradleHandler {
    
    @Override
    public McpSchema.JsonSchema getSchema() {
        Map<String, Object> properties = new HashMap<>();
        
        // Project path property
        Map<String, Object> projectPath = new HashMap<>();
        projectPath.put("type", "string");
        projectPath.put("description", "Absolute path to the Gradle project directory");
        properties.put("projectPath", projectPath);
        
        // Clean build cache property
        Map<String, Object> cleanBuildCache = new HashMap<>();
        cleanBuildCache.put("type", "boolean");
        cleanBuildCache.put("description", "Also clean the Gradle build cache");
        cleanBuildCache.put("default", false);
        properties.put("cleanBuildCache", cleanBuildCache);
        
        // Clean dependencies property
        Map<String, Object> cleanDependencies = new HashMap<>();
        cleanDependencies.put("type", "boolean");
        cleanDependencies.put("description", "Also clean downloaded dependencies (use with caution)");
        cleanDependencies.put("default", false);
        properties.put("cleanDependencies", cleanDependencies);
        
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
                Boolean cleanBuildCache = (Boolean) arguments.getOrDefault("cleanBuildCache", false);
                Boolean cleanDependencies = (Boolean) arguments.getOrDefault("cleanDependencies", false);
                
                logger.info("Executing clean for project: {}", projectPath);
                
                List<String> tasks = new ArrayList<>();
                tasks.add("clean");
                
                List<String> gradleArgs = new ArrayList<>();
                
                if (cleanBuildCache) {
                    gradleArgs.add("--no-build-cache");
                    // Also run cleanBuildCache task if available
                    tasks.add("cleanBuildCache");
                }
                
                // Calculate directory sizes before cleaning
                File buildDir = new File(projectPath, "build");
                long buildDirSizeBefore = calculateDirectorySize(buildDir);
                
                File gradleDir = new File(projectPath, ".gradle");
                long gradleDirSizeBefore = calculateDirectorySize(gradleDir);
                
                BuildResult result = executeBuild(projectPath, tasks, gradleArgs);
                
                // Calculate sizes after cleaning
                long buildDirSizeAfter = calculateDirectorySize(buildDir);
                long gradleDirSizeAfter = calculateDirectorySize(gradleDir);
                
                long totalCleaned = (buildDirSizeBefore - buildDirSizeAfter) + 
                                   (gradleDirSizeBefore - gradleDirSizeAfter);
                
                StringBuilder response = new StringBuilder();
                response.append(result.success ? "CLEAN SUCCESSFUL\n" : "CLEAN FAILED\n");
                response.append("\n");
                
                if (result.success && totalCleaned > 0) {
                    response.append("Cleaned: ").append(formatBytes(totalCleaned)).append("\n");
                    response.append("- Build directory: ").append(formatBytes(buildDirSizeBefore - buildDirSizeAfter)).append("\n");
                    response.append("- Gradle cache: ").append(formatBytes(gradleDirSizeBefore - gradleDirSizeAfter)).append("\n");
                    response.append("\n");
                }
                
                if (!result.output.isEmpty()) {
                    response.append("Output:\n").append(result.output);
                }
                
                if (!result.errors.isEmpty()) {
                    response.append("\nErrors:\n").append(result.errors);
                }
                
                // Handle cleaning dependencies if requested
                if (cleanDependencies && result.success) {
                    response.append("\n\nCleaning dependencies...\n");
                    boolean depsCleanResult = cleanDependenciesCache(projectPath);
                    if (depsCleanResult) {
                        response.append("Dependencies cache cleaned successfully.\n");
                    } else {
                        response.append("Failed to clean dependencies cache.\n");
                    }
                }
                
                return result.success ? 
                    createSuccessResult(response.toString()) : 
                    createErrorResult(response.toString());
                
            } catch (Exception e) {
                logger.error("Error executing clean", e);
                return createErrorResult("Clean failed: " + e.getMessage());
            }
        });
    }
    
    private long calculateDirectorySize(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return 0;
        }
        
        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
    
    private boolean cleanDependenciesCache(String projectPath) {
        try {
            // Clean the .gradle/caches directory in the project
            File projectCaches = new File(projectPath, ".gradle/caches");
            if (projectCaches.exists()) {
                deleteDirectory(projectCaches);
            }
            
            // Note: We don't clean the global Gradle cache as it would affect other projects
            logger.info("Cleaned project-specific dependency cache");
            return true;
            
        } catch (Exception e) {
            logger.error("Error cleaning dependencies cache", e);
            return false;
        }
    }
    
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
