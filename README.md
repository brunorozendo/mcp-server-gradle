# MCP Gradle Server

An MCP (Model Context Protocol) server that exposes Gradle build functionality, allowing AI assistants to interact with Gradle projects through natural language commands.

## Features

- **Build Execution**: Run Gradle builds with custom tasks and arguments
- **Test Execution**: Execute tests with filtering and parallel execution support
- **Task Discovery**: List available Gradle tasks with filtering by group
- **Clean Operations**: Clean build artifacts and caches
- **Dependency Analysis**: Analyze project dependencies by configuration

## Requirements

- Java 21 or higher
- Gradle 8.x (for building the server)
- Target projects should have Gradle wrapper or compatible Gradle installation

## Building

```bash
./gradlew shadowJar
```

This creates a fat JAR in `build/libs/mcp-gradle-server-1.0.0-all.jar`

## Usage

### Running the Server

```bash
java -jar build/libs/mcp-gradle-server-1.0.0-all.jar
```

The server uses stdio transport for communication with MCP clients.

### Available Tools

#### 1. gradle_build
Execute Gradle build tasks.

**Parameters:**
- `projectPath` (required): Absolute path to the Gradle project
- `tasks`: List of tasks to execute (default: ["build"])
- `arguments`: Additional Gradle arguments
- `environment`: Environment variables for the build

**Example:**
```json
{
  "projectPath": "/path/to/project",
  "tasks": ["clean", "build"],
  "arguments": ["--info", "--stacktrace"]
}
```

#### 2. gradle_test
Run Gradle tests with filtering options.

**Parameters:**
- `projectPath` (required): Absolute path to the Gradle project
- `testFilter`: Test filter pattern (e.g., "*IntegrationTest")
- `includeIntegrationTests`: Include integration tests (default: false)
- `parallel`: Run tests in parallel (default: false)
- `arguments`: Additional test arguments

**Example:**
```json
{
  "projectPath": "/path/to/project",
  "testFilter": "com.example.MyTest",
  "parallel": true
}
```

#### 3. gradle_tasks
List available Gradle tasks.

**Parameters:**
- `projectPath` (required): Absolute path to the Gradle project
- `includeAll`: Include all tasks including internal ones (default: false)
- `group`: Filter tasks by group (e.g., "build", "verification")

**Example:**
```json
{
  "projectPath": "/path/to/project",
  "group": "build"
}
```

#### 4. gradle_clean
Clean build artifacts and caches.

**Parameters:**
- `projectPath` (required): Absolute path to the Gradle project
- `cleanBuildCache`: Also clean the Gradle build cache (default: false)
- `cleanDependencies`: Clean downloaded dependencies (default: false)

**Example:**
```json
{
  "projectPath": "/path/to/project",
  "cleanBuildCache": true
}
```

#### 5. gradle_dependencies
Analyze project dependencies.

**Parameters:**
- `projectPath` (required): Absolute path to the Gradle project
- `configuration`: Dependency configuration (default: "compileClasspath")
- `showTransitive`: Show transitive dependencies (default: true)
- `refresh`: Refresh dependencies from repositories (default: false)

**Example:**
```json
{
  "projectPath": "/path/to/project",
  "configuration": "runtimeClasspath"
}
```

## Configuration

### Logging

The server uses Logback for logging. Configuration can be found in `src/main/resources/logback.xml`. Logs are written to:
- Console (INFO level)
- `logs/mcp-gradle-server.log` (DEBUG level)

### Connection Management

The server manages Gradle project connections with:
- Connection pooling and reuse
- Automatic cleanup of stale connections (5-minute timeout)
- Support for Gradle wrapper detection

## Development

### Project Structure

```
src/main/java/io/modelcontextprotocol/gradleserver/
├── GradleMcpServerMain.java          # Main entry point
├── GradleConnectionManager.java      # Connection pooling
└── handlers/
    ├── BaseGradleHandler.java        # Base handler functionality
    ├── GradleBuildHandler.java       # Build execution
    ├── GradleTestHandler.java        # Test execution
    ├── GradleTasksHandler.java       # Task listing
    ├── GradleCleanHandler.java       # Clean operations
    └── GradleDependenciesHandler.java # Dependency analysis
```

### Testing

```bash
./gradlew test
```

### Extending

To add new Gradle tools:

1. Create a new handler class extending `BaseGradleHandler`
2. Implement the `getSchema()` method to define parameters
3. Implement the `handle()` method for tool logic
4. Register the handler in `GradleMcpServerMain.registerHandlers()`

## License

This project is licensed under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
