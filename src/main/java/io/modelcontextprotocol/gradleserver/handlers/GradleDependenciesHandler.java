package io.modelcontextprotocol.gradleserver.handlers;

import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles Gradle dependency operations.
 */
public class GradleDependenciesHandler extends BaseGradleHandler {
    
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
        "([+\\\\|\\s-]+)--- ([^:]+):([^:]+):([^\\s]+)(?:\\s+->\\s+([^\\s]+))?"
    );
    
    @Override
    public McpSchema.JsonSchema getSchema() {
        Map<String, Object> properties = new HashMap<>();
        
        // Project path property
        Map<String, Object> projectPath = new HashMap<>();
        projectPath.put("type", "string");
        projectPath.put("description", "Absolute path to the Gradle project directory");
        properties.put("projectPath", projectPath);
        
        // Configuration property
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("type", "string");
        configuration.put("description", "Dependency configuration to analyze (e.g., compileClasspath, runtimeClasspath, testCompileClasspath)");
        configuration.put("default", "compileClasspath");
        properties.put("configuration", configuration);
        
        // Show transitive property
        Map<String, Object> showTransitive = new HashMap<>();
        showTransitive.put("type", "boolean");
        showTransitive.put("description", "Show transitive dependencies");
        showTransitive.put("default", true);
        properties.put("showTransitive", showTransitive);
        
        // Refresh dependencies property
        Map<String, Object> refresh = new HashMap<>();
        refresh.put("type", "boolean");
        refresh.put("description", "Refresh dependencies from remote repositories");
        refresh.put("default", false);
        properties.put("refresh", refresh);
        
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
                String configuration = (String) arguments.getOrDefault("configuration", "compileClasspath");
                Boolean showTransitive = (Boolean) arguments.getOrDefault("showTransitive", true);
                Boolean refresh = (Boolean) arguments.getOrDefault("refresh", false);
                
                logger.info("Analyzing dependencies for project: {} configuration: {}", 
                    projectPath, configuration);
                
                List<String> tasks = new ArrayList<>();
                tasks.add("dependencies");
                
                List<String> gradleArgs = new ArrayList<>();
                //gradleArgs.add("--configuration");
                gradleArgs.add(configuration);
                
                if (!showTransitive) {
                    gradleArgs.add("--no-transitive");
                }
                
                if (refresh) {
                    gradleArgs.add("--refresh-dependencies");
                }
                
                BuildResult result = executeBuild(projectPath, tasks, gradleArgs);
                
                if (result.success) {
                    // Parse dependencies from output
                    DependencyReport report = parseDependencies(result.output, configuration);
                    
                    StringBuilder response = new StringBuilder();
                    response.append("DEPENDENCIES for configuration: ").append(configuration).append("\n");
                    response.append("=".repeat(50)).append("\n\n");
                    
                    if (report.dependencies.isEmpty()) {
                        response.append("No dependencies found for this configuration.\n");
                    } else {
                        response.append("Direct Dependencies:\n");
                        response.append("-".repeat(20)).append("\n");
                        
                        for (Dependency dep : report.dependencies) {
                            if (dep.level == 0) {
                                response.append(formatDependency(dep)).append("\n");
                            }
                        }
                        
                        response.append("\n");
                        response.append("Total Dependencies: ").append(report.totalCount).append("\n");
                        response.append("Direct Dependencies: ").append(report.directCount).append("\n");
                        response.append("Transitive Dependencies: ").append(report.transitiveCount).append("\n");
                        
                        if (showTransitive && report.transitiveCount > 0) {
                            response.append("\nDependency Tree:\n");
                            response.append("-".repeat(20)).append("\n");
                            response.append(result.output);
                        }
                    }
                    
                    return createSuccessResult(response.toString());
                } else {
                    return createErrorResult(result.getSummary());
                }
                
            } catch (Exception e) {
                logger.error("Error analyzing dependencies", e);
                return createErrorResult("Dependency analysis failed: " + e.getMessage());
            }
        });
    }
    
    private DependencyReport parseDependencies(String output, String configuration) {
        DependencyReport report = new DependencyReport();
        
        String[] lines = output.split("\n");
        boolean inConfiguration = false;
        
        for (String line : lines) {
            // Check if we're entering the desired configuration section
            if (line.contains(configuration + " -")) {
                inConfiguration = true;
                continue;
            }
            
            // Check if we're leaving the configuration section
            if (inConfiguration && line.trim().isEmpty() && !line.startsWith(" ")) {
                break;
            }
            
            if (inConfiguration) {
                Matcher matcher = DEPENDENCY_PATTERN.matcher(line);
                if (matcher.find()) {
                    String prefix = matcher.group(1);
                    String group = matcher.group(2);
                    String name = matcher.group(3);
                    String version = matcher.group(4);
                    String resolvedVersion = matcher.group(5);
                    
                    int level = calculateDependencyLevel(prefix);
                    
                    Dependency dep = new Dependency(
                        group, 
                        name, 
                        version, 
                        resolvedVersion != null ? resolvedVersion : version,
                        level
                    );
                    
                    report.dependencies.add(dep);
                    report.totalCount++;
                    
                    if (level == 0) {
                        report.directCount++;
                    } else {
                        report.transitiveCount++;
                    }
                }
            }
        }
        
        return report;
    }
    
    private int calculateDependencyLevel(String prefix) {
        // Count the number of indentation markers to determine the level
        int level = 0;
        for (char c : prefix.toCharArray()) {
            if (c == '+' || c == '\\' || c == '|' || c == '-') {
                level++;
            }
        }
        return level / 4; // Typically each level is 4 characters
    }
    
    private String formatDependency(Dependency dep) {
        StringBuilder sb = new StringBuilder();
        sb.append(dep.group).append(":").append(dep.name).append(":");
        
        if (!dep.requestedVersion.equals(dep.resolvedVersion)) {
            sb.append(dep.requestedVersion).append(" -> ").append(dep.resolvedVersion);
        } else {
            sb.append(dep.resolvedVersion);
        }
        
        return sb.toString();
    }
    
    private static class DependencyReport {
        final List<Dependency> dependencies = new ArrayList<>();
        int totalCount = 0;
        int directCount = 0;
        int transitiveCount = 0;
    }
    
    private static class Dependency {
        final String group;
        final String name;
        final String requestedVersion;
        final String resolvedVersion;
        final int level;
        
        Dependency(String group, String name, String requestedVersion, 
                  String resolvedVersion, int level) {
            this.group = group;
            this.name = name;
            this.requestedVersion = requestedVersion;
            this.resolvedVersion = resolvedVersion;
            this.level = level;
        }
    }
}
