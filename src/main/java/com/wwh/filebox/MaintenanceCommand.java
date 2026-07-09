package com.wwh.filebox;

import com.wwh.filebox.service.FileBoxConfigStore;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Path;

public final class MaintenanceCommand {

    private static final String MAINTENANCE_PREFIX = "--filebox.maintenance=";
    private static final String RESET_ADMIN_PASSWORD = "reset-admin-password";

    private MaintenanceCommand() {
    }

    public static boolean runIfRequested(String[] args) {
        String command = readMaintenanceCommand(args);
        if (command == null) {
            return false;
        }

        if (!RESET_ADMIN_PASSWORD.equals(command)) {
            System.err.println("Unknown maintenance command: " + command);
            System.exit(2);
            return true;
        }

        try {
            Path configPath = FileBoxConfigStore.resolveConfigPath(FileBoxConfigStore.readConfigPathArg(args));
            FileBoxConfigStore store = new FileBoxConfigStore(configPath, new BCryptPasswordEncoder());
            FileBoxConfigStore.PasswordResetResult result = store.resetAdminPassword();

            System.out.println("Admin password reset successfully.");
            System.out.println("Config file: " + configPath.toAbsolutePath());
            System.out.println("Username: admin");
            System.out.println("New password: " + result.getAdminPassword());
            return true;
        } catch (Exception e) {
            System.err.println("Failed to reset admin password: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
            return true;
        }
    }

    private static String readMaintenanceCommand(String[] args) {
        if (args == null) {
            return null;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith(MAINTENANCE_PREFIX)) {
                return arg.substring(MAINTENANCE_PREFIX.length());
            }
        }
        return null;
    }
}
