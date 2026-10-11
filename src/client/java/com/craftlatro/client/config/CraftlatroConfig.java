package com.craftlatro.client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class CraftlatroConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftlatro");
    private static final String CONFIG_FILE = "craftlatro.properties";

    private static CraftlatroConfig INSTANCE = null;

    private final Path configPath;
    private final Properties properties;

    private CraftlatroConfig() {
        this.configPath = getConfigDirectory().resolve(CONFIG_FILE);
        this.properties = new Properties();
        load();
    }

    public static synchronized CraftlatroConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CraftlatroConfig();
        }
        return INSTANCE;
    }

    public boolean getWarningAcknowledged() {
        return Boolean.parseBoolean(properties.getProperty("warningAcknowledged", "false"));
    }

    public void setWarningAcknowledged(boolean acknowledged) {
        properties.setProperty("warningAcknowledged", String.valueOf(acknowledged));
        save();
    }

    public String getBalatroExePath() {
        return properties.getProperty("balatroExePath");
    }

    public void setBalatroExePath(String path) {
        properties.setProperty("balatroExePath", path);
        save();
    }

    private Path getConfigDirectory() {
        String configDir = System.getProperty("user.home");
        Path configPath = Path.of(configDir, ".craftlatro", "config");
        try {
            Files.createDirectories(configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
        }
        return configPath;
    }

    private void load() {
        try {
            if (Files.exists(configPath)) {
                properties.load(Files.newBufferedReader(configPath));
                LOGGER.info("Loaded config from {}", configPath);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load config, using defaults", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(configPath.getParent());
            properties.store(Files.newBufferedWriter(configPath), "craftlatro config");
            LOGGER.info("Saved config to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
}
