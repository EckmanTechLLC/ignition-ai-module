package com.iai.ignition.gateway.util;

import com.inductiveautomation.ignition.common.util.LoggerEx;

import java.io.File;
import java.util.Optional;

/**
 * Utility class for detecting Ignition Gateway data path.
 */
public class GatewayPathDetector {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.util.GatewayPathDetector");

    /**
     * Common paths where Ignition might be installed.
     */
    private static final String[] COMMON_PATHS = {
        "/var/lib/ignition/data",
        "/usr/local/ignition/data",
        "/opt/ignition/data",
        "C:\\Program Files\\Inductive Automation\\Ignition\\data",
        "C:\\Ignition\\data"
    };

    /**
     * Detect the Ignition Gateway data path.
     *
     * @param manualOverride Optional manual override from settings
     * @return Detected gateway data path, or empty if not found
     */
    public static Optional<String> detectGatewayDataPath(String manualOverride) {
        // 1. Check manual override first
        if (manualOverride != null && !manualOverride.isEmpty()) {
            File overridePath = new File(manualOverride);
            if (overridePath.exists() && overridePath.isDirectory()) {
                logger.info("Using manual gateway data path: " + manualOverride);
                return Optional.of(manualOverride);
            } else {
                logger.warn("Manual gateway data path does not exist: " + manualOverride);
            }
        }

        // 2. Check user.dir/data
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            File userDirData = new File(userDir, "data");
            if (userDirData.exists() && userDirData.isDirectory()) {
                String path = userDirData.getAbsolutePath();
                logger.info("Detected gateway data path from user.dir: " + path);
                return Optional.of(path);
            }
        }

        // 3. Check common installation paths
        for (String commonPath : COMMON_PATHS) {
            File file = new File(commonPath);
            if (file.exists() && file.isDirectory()) {
                logger.info("Detected gateway data path from common paths: " + commonPath);
                return Optional.of(commonPath);
            }
        }

        logger.warn("Could not detect gateway data path. Please configure manually.");
        return Optional.empty();
    }

    /**
     * Get the projects directory path.
     *
     * @param gatewayDataPath The gateway data path
     * @return Projects directory path
     */
    public static String getProjectsPath(String gatewayDataPath) {
        return new File(gatewayDataPath, "projects").getAbsolutePath();
    }

    /**
     * Get a specific project's path.
     *
     * @param gatewayDataPath The gateway data path
     * @param projectName The project name
     * @return Project directory path
     */
    public static String getProjectPath(String gatewayDataPath, String projectName) {
        return new File(getProjectsPath(gatewayDataPath), projectName).getAbsolutePath();
    }

    /**
     * Verify that the gateway data path is valid and accessible.
     *
     * @param gatewayDataPath Path to verify
     * @return true if valid and accessible
     */
    public static boolean verifyGatewayDataPath(String gatewayDataPath) {
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            return false;
        }

        File dataDir = new File(gatewayDataPath);
        if (!dataDir.exists() || !dataDir.isDirectory()) {
            logger.warn("Gateway data path does not exist or is not a directory: " + gatewayDataPath);
            return false;
        }

        File projectsDir = new File(dataDir, "projects");
        if (!projectsDir.exists() || !projectsDir.isDirectory()) {
            logger.warn("Projects directory does not exist in gateway data path: " + projectsDir.getAbsolutePath());
            return false;
        }

        return true;
    }
}
