# MCP Gradle Server - Client Configuration

This document explains how to configure MCP clients to use the Gradle server.

## Claude Desktop Configuration

To use this MCP server with Claude Desktop, add the following to your Claude configuration file:

### macOS
Edit `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "gradle": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/mcp-gradle-server/build/libs/mcp-gradle-server-1.0.0-all.jar"
      ],
      "env": {
        "JAVA_HOME": "/path/to/java21"
      }
    }
  }
}
```

### Windows
Edit `%APPDATA%\Claude\claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "gradle": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\path\\to\\mcp-gradle-server\\build\\libs\\mcp-gradle-server-1.0.0-all.jar"
      ],
      "env": {
        "JAVA_HOME": "C:\\path\\to\\java21"
      }
    }
  }
}
```

### Linux
Edit `~/.config/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "gradle": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/mcp-gradle-server/build/libs/mcp-gradle-server-1.0.0-all.jar"
      ],
      "env": {
        "JAVA_HOME": "/path/to/java21"
      }
    }
  }
}
```

## Using with npx

If you have Node.js installed, you can test the server using the MCP inspector:

```bash
npx @modelcontextprotocol/inspector java -jar /path/to/mcp-gradle-server-1.0.0-all.jar
```

This will open a web interface where you can interact with the server and test its tools.

## Environment Variables

The server supports the following environment variables:

- `JAVA_HOME`: Path to Java 21 installation (required if not in PATH)
- `GRADLE_USER_HOME`: Custom Gradle user home directory
- `GRADLE_OPTS`: JVM options for Gradle builds

## Example Usage in Claude

Once configured, you can use commands like:

- "List all gradle tasks in /path/to/project"
- "Build the project at /path/to/project"
- "Run tests in /path/to/project"
- "Show dependencies for /path/to/project"
- "Clean the build directory of /path/to/project"

## Troubleshooting

### Server doesn't start
1. Ensure Java 21 is installed: `java -version`
2. Check the JAR file exists at the specified path
3. Look at logs in `logs/mcp-gradle-server.log`

### Gradle operations fail
1. Ensure the project has valid Gradle build files
2. Check if Gradle wrapper is present or Gradle is installed
3. Verify file permissions on the project directory

### Connection issues
1. Check Claude's logs for MCP errors
2. Test the server standalone: `java -jar mcp-gradle-server-1.0.0-all.jar`
3. Use the MCP inspector to debug communication
