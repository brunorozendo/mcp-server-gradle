package io.modelcontextprotocol.gradleserver.handlers;

import io.modelcontextprotocol.spec.McpSchema;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import reactor.core.publisher.Mono;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles listing available Gradle tasks in a project.
 */
public class GradleTasksHandler extends BaseGradleHandler {
    
    @Override
    public McpSchema.JsonSchema getSchema() {
        Map<String, Object> properties = new HashMap<>();
        
        // Project path property
        Map<String, Object> projectPath = new HashMap<>();
        projectPath.put("type", "string");
        projectPath.put("description", "Absolute path to the Gradle project directory");
        properties.put("projectPath", projectPath);
        
        // Include all tasks property
        Map<String, Object> includeAll = new HashMap<>();
        includeAll.put("type", "boolean");
        includeAll.put("description", "Include all tasks (including those from plugins)");
        includeAll.put("default", false);
        properties.put("includeAll", includeAll);
        
        // Group filter property
        Map<String, Object> group = new HashMap<>();
        group.put("type", "string");
        group.put("description", "Filter tasks by group (e.g., build, documentation, verification)");
        properties.put("group", group);
        
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
                Boolean includeAll = (Boolean) arguments.getOrDefault("includeAll", false);
                String groupFilter = (String) arguments.get("group");
                
                logger.info("Listing tasks for project: {}", projectPath);
                
                // Use model query for more detailed task information
                List<TaskInfo> tasks = getProjectTasks(projectPath, includeAll, groupFilter);
                
                StringBuilder response = new StringBuilder();
                response.append("Available Gradle Tasks\n");
                response.append("=====================\n\n");
                
                if (tasks.isEmpty()) {
                    response.append("No tasks found matching the criteria.\n");
                } else {
                    // Group tasks by their group
                    Map<String, List<TaskInfo>> tasksByGroup = tasks.stream()
                        .collect(Collectors.groupingBy(t -> t.group != null ? t.group : "Other"));
                    
                    // Sort groups alphabetically
                    List<String> sortedGroups = new ArrayList<>(tasksByGroup.keySet());
                    sortedGroups.sort(String::compareToIgnoreCase);
                    
                    for (String group : sortedGroups) {
                        response.append(group).append(" tasks\n");
                        response.append("-".repeat(group.length() + 6)).append("\n");
                        
                        List<TaskInfo> groupTasks = tasksByGroup.get(group);
                        groupTasks.sort(Comparator.comparing(t -> t.name));
                        
                        for (TaskInfo task : groupTasks) {
                            response.append(String.format("%-30s %s\n", 
                                task.name, 
                                task.description != null ? task.description : ""));
                        }
                        response.append("\n");
                    }
                    
                    response.append("\nTotal tasks: ").append(tasks.size()).append("\n");
                    
                    if (!includeAll) {
                        response.append("\nTip: Use includeAll=true to see all tasks including internal ones.\n");
                    }
                }
                
                return createSuccessResult(response.toString());
                
            } catch (Exception e) {
                logger.error("Error listing tasks", e);
                return createErrorResult("Failed to list tasks: " + e.getMessage());
            }
        });
    }
    
    private List<TaskInfo> getProjectTasks(String projectPath, boolean includeAll, String groupFilter) {
        validateProjectPath(projectPath);
        
        ProjectConnection connection = connectionManager.getConnection(projectPath);
        
        try {
            // Get the Gradle project model
            GradleProject project = connection.getModel(GradleProject.class);
            
            List<TaskInfo> tasks = new ArrayList<>();
            collectTasks(project, tasks, includeAll, groupFilter, "");
            
            return tasks;
            
        } catch (Exception e) {
            logger.error("Error getting project model", e);
            
            // Fallback to using tasks command
            return getTasksViaCommand(projectPath, includeAll, groupFilter);
        }
    }
    
    private void collectTasks(GradleProject project, List<TaskInfo> tasks, 
                             boolean includeAll, String groupFilter, String prefix) {
        for (GradleTask task : project.getTasks()) {
            String taskPath = prefix.isEmpty() ? task.getName() : prefix + ":" + task.getName();
            
            // Filter by group if specified
            if (groupFilter != null && !groupFilter.isEmpty()) {
                if (task.getGroup() == null || !task.getGroup().equalsIgnoreCase(groupFilter)) {
                    continue;
                }
            }
            
            // Skip private tasks unless includeAll is true
            if (!includeAll && task.isPublic() == false) {
                continue;
            }
            
            tasks.add(new TaskInfo(
                taskPath,
                task.getGroup(),
                task.getDescription(),
                task.isPublic()
            ));
        }
        
        // Recursively collect tasks from subprojects
        for (GradleProject subproject : project.getChildren()) {
            String subPrefix = prefix.isEmpty() ? subproject.getName() : prefix + ":" + subproject.getName();
            collectTasks(subproject, tasks, includeAll, groupFilter, subPrefix);
        }
    }
    
    private List<TaskInfo> getTasksViaCommand(String projectPath, boolean includeAll, String groupFilter) {
        List<String> gradleArgs = new ArrayList<>();
        if (includeAll) {
            gradleArgs.add("--all");
        }
        
        BuildResult result = executeBuild(projectPath, List.of("tasks"), gradleArgs);
        
        // Parse the output to extract task information
        List<TaskInfo> tasks = new ArrayList<>();
        String[] lines = result.output.split("\n");
        
        String currentGroup = null;
        for (String line : lines) {
            line = line.trim();
            
            // Check if this is a group header
            if (line.endsWith(" tasks") && !line.startsWith("-")) {
                currentGroup = line.replace(" tasks", "");
                continue;
            }
            
            // Skip separator lines and empty lines
            if (line.startsWith("-") || line.isEmpty()) {
                continue;
            }
            
            // Parse task line (format: "taskName - description")
            String[] parts = line.split(" - ", 2);
            if (parts.length > 0 && !parts[0].isEmpty()) {
                String taskName = parts[0].trim();
                String description = parts.length > 1 ? parts[1].trim() : null;
                
                if (groupFilter == null || groupFilter.isEmpty() || 
                    (currentGroup != null && currentGroup.equalsIgnoreCase(groupFilter))) {
                    tasks.add(new TaskInfo(taskName, currentGroup, description, true));
                }
            }
        }
        
        return tasks;
    }
    
    private static class TaskInfo {
        final String name;
        final String group;
        final String description;
        final boolean isPublic;
        
        TaskInfo(String name, String group, String description, boolean isPublic) {
            this.name = name;
            this.group = group;
            this.description = description;
            this.isPublic = isPublic;
        }
    }
}
