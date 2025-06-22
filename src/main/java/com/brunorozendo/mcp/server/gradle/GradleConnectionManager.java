package com.brunorozendo.mcp.server.gradle;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Gradle project connections with caching and lifecycle management.
 * Connections are cached and reused for performance, with automatic cleanup of stale connections.
 */
public class GradleConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(GradleConnectionManager.class);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofMinutes(5);
    
    private final Map<String, CachedConnection> connections = new ConcurrentHashMap<>();
    private static final GradleConnectionManager INSTANCE = new GradleConnectionManager();
    
    private GradleConnectionManager() {
        // Start cleanup thread
        Thread cleanupThread = new Thread(this::cleanupStaleConnections);
        cleanupThread.setDaemon(true);
        cleanupThread.setName("gradle-connection-cleanup");
        cleanupThread.start();
    }
    
    public static GradleConnectionManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Gets or creates a project connection for the specified project directory.
     */
    public ProjectConnection getConnection(String projectPath) {
        String normalizedPath = new File(projectPath).getAbsolutePath();
        
        CachedConnection cached = connections.compute(normalizedPath, (path, existing) -> {
            if (existing != null && !existing.isExpired()) {
                existing.updateLastUsed();
                return existing;
            }
            
            // Close existing expired connection
            if (existing != null) {
                try {
                    existing.connection.close();
                } catch (Exception e) {
                    logger.warn("Error closing expired connection for {}", path, e);
                }
            }
            
            // Create new connection
            logger.info("Creating new Gradle connection for: {}", path);
            ProjectConnection connection = createConnection(path);
            return new CachedConnection(connection);
        });
        
        return cached.connection;
    }
    
    private ProjectConnection createConnection(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            throw new IllegalArgumentException("Project directory does not exist: " + projectPath);
        }
        
        // Check for Gradle build files
        boolean hasGradleFiles = new File(projectDir, "build.gradle").exists() ||
                                new File(projectDir, "build.gradle.kts").exists() ||
                                new File(projectDir, "settings.gradle").exists() ||
                                new File(projectDir, "settings.gradle.kts").exists();
        
        if (!hasGradleFiles) {
            throw new IllegalArgumentException("No Gradle build files found in: " + projectPath);
        }
        
        GradleConnector connector = GradleConnector.newConnector()
            .forProjectDirectory(projectDir)
            .useBuildDistribution(); // Use the Gradle wrapper if available
        
        return connector.connect();
    }
    
    /**
     * Closes a specific connection and removes it from the cache.
     */
    public void closeConnection(String projectPath) {
        String normalizedPath = new File(projectPath).getAbsolutePath();
        CachedConnection cached = connections.remove(normalizedPath);
        
        if (cached != null) {
            try {
                cached.connection.close();
                logger.info("Closed connection for: {}", normalizedPath);
            } catch (Exception e) {
                logger.error("Error closing connection for {}", normalizedPath, e);
            }
        }
    }
    
    /**
     * Closes all connections and clears the cache.
     */
    public void closeAll() {
        logger.info("Closing all {} Gradle connections", connections.size());
        
        connections.forEach((path, cached) -> {
            try {
                cached.connection.close();
            } catch (Exception e) {
                logger.error("Error closing connection for {}", path, e);
            }
        });
        
        connections.clear();
    }
    
    private void cleanupStaleConnections() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(Duration.ofMinutes(1).toMillis());
                
                connections.entrySet().removeIf(entry -> {
                    if (entry.getValue().isExpired()) {
                        logger.info("Removing stale connection for: {}", entry.getKey());
                        try {
                            entry.getValue().connection.close();
                        } catch (Exception e) {
                            logger.warn("Error closing stale connection", e);
                        }
                        return true;
                    }
                    return false;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private static class CachedConnection {
        final ProjectConnection connection;
        Instant lastUsed;
        
        CachedConnection(ProjectConnection connection) {
            this.connection = connection;
            this.lastUsed = Instant.now();
        }
        
        void updateLastUsed() {
            this.lastUsed = Instant.now();
        }
        
        boolean isExpired() {
            return Duration.between(lastUsed, Instant.now()).compareTo(CONNECTION_TIMEOUT) > 0;
        }
    }
}
