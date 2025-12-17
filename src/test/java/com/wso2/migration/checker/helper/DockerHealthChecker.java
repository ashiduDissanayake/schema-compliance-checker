package com.wso2.migration.checker.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for checking Docker daemon health and availability.
 */
public final class DockerHealthChecker {

    private static final Logger LOG = LoggerFactory.getLogger(DockerHealthChecker.class);

    private DockerHealthChecker() {}

    /**
     * Checks if Docker daemon is running and accessible.
     */
    public static boolean isDockerAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "info");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);

            if (! finished) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            LOG.debug("Docker check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets the Docker version if available.
     */
    public static String getDockerVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "--version");
            Process process = pb.start();
            String output = new String(process. getInputStream().readAllBytes());
            process.waitFor(5, TimeUnit.SECONDS);
            return output.trim();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * Checks if a specific Docker image is available locally.
     */
    public static boolean isImageAvailable(String imageName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "image", "inspect", imageName);
            pb.redirectErrorStream(true);
            Process process = pb. start();
            process.waitFor(10, TimeUnit.SECONDS);
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the count of running containers.
     */
    public static int getRunningContainerCount() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "ps", "-q");
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor(5, TimeUnit.SECONDS);

            if (output.trim().isEmpty()) {
                return 0;
            }
            return output.trim().split("\n").length;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Stops all containers with a specific name pattern.
     */
    public static void stopContainersByPattern(String pattern) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "ps", "-q", "--filter", "name=" + pattern
            );
            Process process = pb.start();
            String containerIds = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor(5, TimeUnit.SECONDS);

            if (! containerIds.isEmpty()) {
                for (String containerId : containerIds. split("\n")) {
                    new ProcessBuilder("docker", "stop", containerId).start().waitFor(30, TimeUnit.SECONDS);
                    LOG.info("Stopped container: {}", containerId);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to stop containers:  {}", e.getMessage());
        }
    }
}