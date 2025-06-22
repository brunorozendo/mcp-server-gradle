package com.brunorozendo.mcp.server.gradle;

import com.brunorozendo.mcp.server.gradle.handlers.GradleBuildHandler;
import com.brunorozendo.mcp.server.gradle.handlers.GradleCleanHandler;
import com.brunorozendo.mcp.server.gradle.handlers.GradleDependenciesHandler;
import com.brunorozendo.mcp.server.gradle.handlers.GradleTasksHandler;
import com.brunorozendo.mcp.server.gradle.handlers.GradleTestHandler;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Main entry point for the MCP Gradle Server.
 * This server exposes Gradle build functionality through the Model Context Protocol.
 */
public class GradleMcpServerMain {
    private static final Logger logger = LoggerFactory.getLogger(GradleMcpServerMain.class);

    public static void main(String[] args) {
        try {
            logger.info("Starting MCP Gradle Server v1.0.0");

            // Create the transport provider (stdio for command-line usage)
            StdioServerTransportProvider transportProvider = new StdioServerTransportProvider();

            // Create handler instances
            GradleBuildHandler buildHandler = new GradleBuildHandler();
            GradleTestHandler testHandler = new GradleTestHandler();
            GradleTasksHandler tasksHandler = new GradleTasksHandler();
            GradleCleanHandler cleanHandler = new GradleCleanHandler();
            GradleDependenciesHandler dependenciesHandler = new GradleDependenciesHandler();

            // Create and configure the server using the MCP SDK factory
            McpAsyncServer server = McpServer.async(transportProvider)
                .serverInfo("mcp-gradle-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                    .tools(true)  // Enable tools capability with listChanged = true
                    .build())
                // Register tools with proper signatures
                .tool(
                    new McpSchema.Tool("gradle_build", "Execute Gradle build tasks", buildHandler.getSchema()),
                    (McpAsyncServerExchange exchange, Map<String, Object> arguments) -> {
                        logger.debug("REQUEST [gradle_build]: {}", arguments);
                        return buildHandler.handle(arguments)
                            .doOnSuccess(result -> logger.debug("RESPONSE [gradle_build]: {}", result));
                    }
                )
                .tool(
                    new McpSchema.Tool("gradle_test", "Run Gradle tests", testHandler.getSchema()),
                    (McpAsyncServerExchange exchange, Map<String, Object> arguments) -> {
                        logger.debug("REQUEST [gradle_test]: {}", arguments);
                        return testHandler.handle(arguments)
                            .doOnSuccess(result -> logger.debug("RESPONSE [gradle_test]: {}", result));
                    }
                )
                .tool(
                    new McpSchema.Tool("gradle_tasks", "List available Gradle tasks", tasksHandler.getSchema()),
                    (McpAsyncServerExchange exchange, Map<String, Object> arguments) -> {
                        logger.debug("REQUEST [gradle_tasks]: {}", arguments);
                        return tasksHandler.handle(arguments)
                            .doOnSuccess(result -> logger.debug("RESPONSE [gradle_tasks]: {}", result));
                    }
                )
                .tool(
                    new McpSchema.Tool("gradle_clean", "Clean Gradle build output", cleanHandler.getSchema()),
                    (McpAsyncServerExchange exchange, Map<String, Object> arguments) -> {
                        logger.debug("REQUEST [gradle_clean]: {}", arguments);
                        return cleanHandler.handle(arguments)
                            .doOnSuccess(result -> logger.debug("RESPONSE [gradle_clean]: {}", result));
                    }
                )
                .tool(
                    new McpSchema.Tool("gradle_dependencies", "Show Gradle dependencies", dependenciesHandler.getSchema()),
                    (McpAsyncServerExchange exchange, Map<String, Object> arguments) -> {
                        logger.debug("REQUEST [gradle_dependencies]: {}", arguments);
                        return dependenciesHandler.handle(arguments)
                            .doOnSuccess(result -> logger.debug("RESPONSE [gradle_dependencies]: {}", result));
                    }
                )
                .build();

            logger.info("MCP Gradle Server initialized with 5 tools");

            // The server will run until the process is terminated
            // The StdioServerTransportProvider handles the connection lifecycle

            // Add shutdown hook for clean shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down MCP Gradle Server");
                server.close();
            }));

            // Keep the main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Fatal error in MCP Gradle Server", e);
            System.exit(1);
        }
    }
}
