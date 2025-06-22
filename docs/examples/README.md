# MCP Gradle Server Examples

This directory contains examples of using the MCP Gradle Server.

## Basic Usage Examples

### 1. Listing Available Tasks

Request:
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "gradle_tasks",
    "arguments": {
      "projectPath": "/path/to/your/gradle/project"
    }
  },
  "id": 1
}
```

Response will include all available Gradle tasks grouped by category.

### 2. Building a Project

Request:
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "gradle_build",
    "arguments": {
      "projectPath": "/path/to/your/gradle/project",
      "tasks": ["clean", "build"],
      "arguments": ["--info"]
    }
  },
  "id": 2
}
```

This will execute `gradle clean build --info` in the specified project.

### 3. Running Tests with Filtering

Request:
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "gradle_test",
    "arguments": {
      "projectPath": "/path/to/your/gradle/project",
      "testFilter": "*IntegrationTest",
      "parallel": true
    }
  },
  "id": 3
}
```

This runs only tests matching the pattern with parallel execution enabled.

### 4. Analyzing Dependencies

Request:
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "gradle_dependencies",
    "arguments": {
      "projectPath": "/path/to/your/gradle/project",
      "configuration": "runtimeClasspath",
      "showTransitive": true
    }
  },
  "id": 4
}
```

Shows the complete dependency tree for the runtime classpath.

### 5. Cleaning with Cache Cleanup

Request:
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "gradle_clean",
    "arguments": {
      "projectPath": "/path/to/your/gradle/project",
      "cleanBuildCache": true
    }
  },
  "id": 5
}
```

Cleans build artifacts and also clears the build cache.

## Advanced Examples

### Building Multiple Projects

You can build multiple projects sequentially by making multiple tool calls:

```javascript
// Pseudo-code for a client
async function buildProjects(projects) {
  for (const project of projects) {
    const result = await mcpClient.callTool({
      name: "gradle_build",
      arguments: {
        projectPath: project.path,
        tasks: project.tasks || ["build"]
      }
    });
    console.log(`${project.name}: ${result.success ? "SUCCESS" : "FAILED"}`);
  }
}
```

### Conditional Build Based on Test Results

```javascript
// Run tests first
const testResult = await mcpClient.callTool({
  name: "gradle_test",
  arguments: { projectPath: "/project" }
});

// Only build if tests pass
if (!testResult.isError) {
  const buildResult = await mcpClient.callTool({
    name: "gradle_build",
    arguments: {
      projectPath: "/project",
      tasks: ["assemble", "publishToMavenLocal"]
    }
  });
}
```

## Error Handling

The server provides detailed error messages when operations fail:

```json
{
  "content": [{
    "type": "text",
    "text": "Build failed: Task 'nonexistentTask' not found in root project"
  }],
  "isError": true
}
```

Common error scenarios:
- Invalid project path
- Missing Gradle files
- Task not found
- Build failures
- Dependency resolution issues

## Performance Tips

1. **Connection Reuse**: The server caches Gradle connections for 5 minutes
2. **Parallel Builds**: Use `arguments: ["--parallel"]` for multi-module projects
3. **Daemon Usage**: Gradle daemon is enabled by default for better performance
4. **Selective Testing**: Use test filters to run only necessary tests

## Integration Patterns

### CI/CD Integration

```yaml
# Example GitHub Action using MCP
- name: Build with MCP Gradle Server
  run: |
    echo '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"gradle_build","arguments":{"projectPath":"${{ github.workspace }}","tasks":["build"]}},"id":1}' | \
    java -jar mcp-gradle-server.jar
```

### IDE Integration

IDEs can use the MCP server to provide Gradle functionality:

```typescript
class GradleIntegration {
  async getAvailableTasks(projectPath: string) {
    const response = await this.mcp.callTool({
      name: "gradle_tasks",
      arguments: { projectPath, includeAll: true }
    });
    return this.parseTasksFromResponse(response);
  }
}
```
