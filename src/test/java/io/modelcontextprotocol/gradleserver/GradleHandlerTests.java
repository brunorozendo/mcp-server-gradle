package io.modelcontextprotocol.gradleserver;

import io.modelcontextprotocol.gradleserver.handlers.*;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for MCP Gradle Server handlers.
 */
class GradleHandlerTests {

    @TempDir
    Path tempDir;
    
    private String projectPath;

    @BeforeEach
    void setUp() throws IOException {
        // Create a simple Gradle project structure
        projectPath = tempDir.toAbsolutePath().toString();

        // Create build.gradle
        String buildGradle = """
            plugins {
                id 'java'
            }
            
            group = 'com.example'
            version = '1.0.0'
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation 'org.slf4j:slf4j-api:2.0.9'
                testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
            }
            
            test {
                useJUnitPlatform()
            }
            """;
        Files.writeString(tempDir.resolve("build.gradle"), buildGradle);

        // Create settings.gradle
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'test-project'");

        // Create source directories
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.createDirectories(tempDir.resolve("src/test/java"));

        // Create a simple Java file
        String javaFile = """
            package com.example;
            
            public class HelloWorld {
                public String greet(String name) {
                    return "Hello, " + name + "!";
                }
            }
            """;
        Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
        Files.writeString(tempDir.resolve("src/main/java/com/example/HelloWorld.java"), javaFile);

        // Create a test file
        String testFile = """
            package com.example;
            
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;
            
            class HelloWorldTest {
                @Test
                void testGreet() {
                    HelloWorld hw = new HelloWorld();
                    assertEquals("Hello, World!", hw.greet("World"));
                }
            }
            """;
        Files.createDirectories(tempDir.resolve("src/test/java/com/example"));
        Files.writeString(tempDir.resolve("src/test/java/com/example/HelloWorldTest.java"), testFile);
    }
    
    @Test
    void testBuildHandler() {
        GradleBuildHandler handler = new GradleBuildHandler();
        
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("projectPath", projectPath);
        arguments.put("tasks", List.of("build"));
        
        McpSchema.CallToolResult result = handler.handle(arguments).block();

        // Debug: Print the error if it fails
        if (result.isError()) {
            McpSchema.TextContent content = (McpSchema.TextContent) result.content().get(0);
            System.err.println("Handler failed with error: " + content.text());
        }



        assertNotNull(result);
        assertFalse(result.isError());
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        
        // Check that build directory was created
        assertTrue(new File(projectPath, "build").exists());
    }
    
    @Test
    void testTasksHandler() {
        GradleTasksHandler handler = new GradleTasksHandler();
        
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("projectPath", projectPath);
        arguments.put("includeAll", false);
        
        McpSchema.CallToolResult result = handler.handle(arguments).block();
        
        assertNotNull(result);
        assertFalse(result.isError());
        
        McpSchema.TextContent content = (McpSchema.TextContent) result.content().get(0);
        String output = content.text();
        
        // Should contain common Gradle tasks
        assertThat(output).contains("build");
        assertThat(output).contains("clean");
        assertThat(output).contains("test");
    }
    
    @Test
    void testCleanHandler() {
        GradleCleanHandler handler = new GradleCleanHandler();
        
        // First create build directory
        new File(projectPath, "build").mkdirs();
        
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("projectPath", projectPath);
        
        McpSchema.CallToolResult result = handler.handle(arguments).block();
        
        assertNotNull(result);
        assertFalse(result.isError());
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
    }
    
//    @Test
//    void testDependenciesHandler() {
//        GradleDependenciesHandler handler = new GradleDependenciesHandler();
//
//        Map<String, Object> arguments = new HashMap<>();
//        arguments.put("projectPath", projectPath);
//        arguments.put("configuration", "compileClasspath");
//
//        McpSchema.CallToolResult result = handler.handle(arguments).block();
//
//        assertNotNull(result);
//
//        // Debug: Print the error if it fails
//        if (result.isError()) {
//            McpSchema.TextContent content = (McpSchema.TextContent) result.content().get(0);
//            System.err.println("Handler failed with error: " + content.text());
//        }
//
//
//        assertFalse(result.isError());
//
//        McpSchema.TextContent content = (McpSchema.TextContent) result.content().get(0);
//        String output = content.text();
//
//        // Should contain the declared dependency
//        assertThat(output).contains("slf4j-api");
//    }
//
    @Test
    void testConnectionManager() {
        GradleConnectionManager manager = GradleConnectionManager.getInstance();
        
        // Test that we can get a connection
        assertDoesNotThrow(() -> {
            manager.getConnection(projectPath);
        });
        
        // Test invalid path
        assertThrows(IllegalArgumentException.class, () -> {
            manager.getConnection("/invalid/path/that/does/not/exist");
        });
    }
    
    @Test
    void testSchemaValidation() {
        // Test that all handlers provide valid schemas
        BaseGradleHandler[] handlers = {
            new GradleBuildHandler(),
            new GradleTestHandler(),
            new GradleTasksHandler(),
            new GradleCleanHandler(),
            new GradleDependenciesHandler()
        };
        
        for (BaseGradleHandler handler : handlers) {
            McpSchema.JsonSchema schema = handler.getSchema();
            assertNotNull(schema);
            assertNotNull(schema.type());
            assertNotNull(schema.properties());
            assertNotNull(schema.required());
            assertTrue(schema.required().contains("projectPath"));
        }
    }
}
