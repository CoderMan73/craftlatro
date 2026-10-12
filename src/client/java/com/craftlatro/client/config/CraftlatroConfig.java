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

    public static final double DEFAULT_BROWSER_RENDER_SCALE = 0.75;
    public static final double MIN_BROWSER_RENDER_SCALE = 0.25;
    public static final double MAX_BROWSER_RENDER_SCALE = 1.0;

    public static final int DEFAULT_BROWSER_FRAME_RATE = 60;
    public static final int MIN_BROWSER_FRAME_RATE = 10;
    public static final int MAX_BROWSER_FRAME_RATE = 240;

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

    /**
     * Browser texture resolution as a fraction of the framebuffer resolution the screen occupies.
     * Lower values shrink the Chromium offscreen surface, which is the dominant cost while the
     * Balatro screen is open. The browser stretches the smaller texture back up, so the trade is
     * sharpness for frame rate.
     */
    public double getBrowserRenderScale() {
        return parseDouble("browserRenderScale");
    }

    public void setBrowserRenderScale(double scale) {
        double clamped = clampBrowserRenderScale(scale);
        properties.setProperty("browserRenderScale", String.valueOf(clamped));
        save();
    }

    /**
     * How often Chromium repaints the offscreen surface, in frames per second. Chromium defaults an
     * offscreen surface that was created without explicit settings to a low rate, which is why the
     * game can look locked near 30 fps while Minecraft itself keeps up.
     */
    public int getBrowserFrameRate() {
        return parseInt("browserFrameRate", DEFAULT_BROWSER_FRAME_RATE, MIN_BROWSER_FRAME_RATE, MAX_BROWSER_FRAME_RATE);
    }

    public void setBrowserFrameRate(int frameRate) {
        properties.setProperty("browserFrameRate",
                String.valueOf(Math.clamp(frameRate, MIN_BROWSER_FRAME_RATE, MAX_BROWSER_FRAME_RATE)));
        save();
    }

    public boolean isStatsOverlayEnabled() {
        return Boolean.parseBoolean(properties.getProperty("statsOverlay", "false"));
    }

    public void setStatsOverlayEnabled(boolean enabled) {
        properties.setProperty("statsOverlay", String.valueOf(enabled));
        save();
    }

    public static double clampBrowserRenderScale(double scale) {
        if (!Double.isFinite(scale)) {
            return DEFAULT_BROWSER_RENDER_SCALE;
        }
        return Math.clamp(scale, MIN_BROWSER_RENDER_SCALE, MAX_BROWSER_RENDER_SCALE);
    }

    private double parseDouble(String key) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_BROWSER_RENDER_SCALE;
        }
        try {
            return clampBrowserRenderScale(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException malformed) {
            LOGGER.warn("Invalid config value for {}: {}", key, raw);
            return DEFAULT_BROWSER_RENDER_SCALE;
        }
    }

    private int parseInt(String key, int fallback, int min, int max) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.clamp(Integer.parseInt(raw.trim()), min, max);
        } catch (NumberFormatException malformed) {
            LOGGER.warn("Invalid config value for {}: {}", key, raw);
            return fallback;
        }
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
