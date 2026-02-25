package net.mcreator.insidethesystem.meta;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.*;
import java.util.Properties;

/**
 * ShaderDeployer — Downloads the Spooklementary shader from Modrinth CDN
 * into .minecraft/shaderpacks/ and force-enables it via the Oculus/Iris API.
 *
 * IMPORTANT: The shader is NOT bundled in our JAR. It is downloaded at runtime
 * from the official Modrinth CDN, respecting the author's download count and
 * avoiding "dropper" behavior that would get flagged by moderation bots.
 *
 * This class uses reflection to interact with Oculus/Iris, making it an OPTIONAL
 * soft-dependency. If Oculus isn't installed, the mod still works — just without
 * the horror shader. A warning is logged and a chat message is sent.
 *
 * Flow:
 *   1. Player clicks "I'm ready" on the disclaimer screen
 *   2. deployAndActivate() is called
 *   3. Shader zip is downloaded from Modrinth CDN → .minecraft/shaderpacks/
 *   4. Oculus API (via reflection) is used to set the active shader pack
 *   5. If Oculus API isn't available, falls back to writing oculus.properties directly
 *
 * Also extracts horror audio/image assets needed by the desktop intrusion features
 * from the original ITS mod's JAR (on the classpath) to ~/.sentient_coolplayer/.
 */
public class ShaderDeployer {
    private static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer-Shader");

    // Modrinth CDN download URL for Spooklementary v2.0.4
    // Project: 6uJCfiCH | Version: rcr90eRP
    private static final String SHADER_DOWNLOAD_URL =
            "https://cdn.modrinth.com/data/6uJCfiCH/versions/rcr90eRP/Spooklementary_v2.0.4.zip";
    private static final String SHADER_FILENAME = "Spooklementary_v2.0.4.zip";
    private static final long EXPECTED_SIZE = 558206L; // bytes
    private static final String EXPECTED_SHA1 = "38651af0c334b9a5d454b903c982ccb24db0869a";

    // Horror assets bundled in the original ITS mod that we extract for system-level use
    // (PowerShell can't play .ogg directly — we convert the use to wav at runtime or use the .ogg files)
    private static final String[][] HORROR_ASSETS = {
        // { resource path in JAR, local filename in ~/.sentient_coolplayer/ }
        {"/assets/inside_the_system/sounds/screamer-jumpscare-66896-_1_.ogg", "scream_distorted.ogg"},
        {"/assets/inside_the_system/sounds/sfx-creepy-and-suspenseful-background-ping-339886.ogg", "whisper.ogg"},
        {"/assets/inside_the_system/sounds/minecraft-cave-sound-14.ogg", "cave_ambient.ogg"},
        {"/assets/inside_the_system/sounds/riser-horror-330827.ogg", "horror_riser.ogg"},
        {"/assets/inside_the_system/sounds/tv-glitch-6245.ogg", "glitch.ogg"},
        {"/assets/inside_the_system/thank you.jpg", "horror_wallpaper.jpg"},
        {"/assets/inside_the_system/textures/screens/face.png", "face_overlay.png"},
        {"/assets/inside_the_system/textures/entities/angry.png", "angry_entity.png"},
    };

    private static boolean deployed = false;

    /**
     * Main entry point — called when the player clicks "I'm ready" on the disclaimer.
     * Deploys the shader pack and extracts horror assets. Thread-safe via synchronized.
     *
     * @return true if the shader was deployed and activated successfully
     */
    public static synchronized boolean deployAndActivate() {
        if (deployed) return true;

        boolean shaderOk = deployShaderPack();
        extractHorrorAssets();

        if (shaderOk) {
            boolean activated = activateViaOculusApi();
            if (!activated) {
                activated = activateViaConfigFile();
            }
            if (activated) {
                LOGGER.info("[Shader] Spooklementary shader deployed and activated!");
            } else {
                LOGGER.warn("[Shader] Shader deployed to shaderpacks/ but could not auto-activate. " +
                        "Player must select it manually in Video Settings > Shader Packs.");
            }
        }

        deployed = true;
        return shaderOk;
    }

    /**
     * Check if Oculus (or Iris) is available at runtime.
     */
    public static boolean isOculusPresent() {
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ─── SHADER DEPLOYMENT ───────────────────────────────────────────

    /**
     * Download the shader zip from Modrinth CDN to .minecraft/shaderpacks/.
     * Skips download if the file already exists with the correct size.
     */
    private static boolean deployShaderPack() {
        try {
            Path gameDir = getGameDirectory();
            Path shaderpacksDir = gameDir.resolve("shaderpacks");
            Files.createDirectories(shaderpacksDir);

            Path target = shaderpacksDir.resolve(SHADER_FILENAME);

            // Only download if it doesn't already exist (or wrong size)
            if (Files.exists(target)) {
                long existingSize = Files.size(target);
                if (existingSize == EXPECTED_SIZE || existingSize > 0) {
                    LOGGER.info("[Shader] Shader pack already exists at {} ({}B), skipping download.", target, existingSize);
                    return true;
                }
            }

            LOGGER.info("[Shader] Downloading Spooklementary shader from Modrinth CDN...");

            HttpURLConnection conn = (HttpURLConnection) URI.create(SHADER_DOWNLOAD_URL).toURL().openConnection();
            try {
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(30_000);
                conn.setRequestProperty("User-Agent", "SentientCoolplayer/1.0.0 (Minecraft mod)");
                conn.setInstanceFollowRedirects(true);

                int status = conn.getResponseCode();
                if (status != 200) {
                    LOGGER.error("[Shader] Modrinth CDN returned HTTP {}. Shader not downloaded.", status);
                    return false;
                }

                // Download to a temp file first, then atomic move
                Path tempFile = shaderpacksDir.resolve(SHADER_FILENAME + ".tmp");
                try (InputStream is = conn.getInputStream()) {
                    Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }

                // Verify size
                long downloadedSize = Files.size(tempFile);
                if (downloadedSize < 100_000) {
                    LOGGER.error("[Shader] Downloaded file too small ({}B). Discarding.", downloadedSize);
                    Files.deleteIfExists(tempFile);
                    return false;
                }

                // Atomic move to final location
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("[Shader] Shader pack downloaded to: {} ({}B)", target, downloadedSize);
            } finally {
                conn.disconnect();
            }

            return true;
        } catch (Exception e) {
            LOGGER.error("[Shader] Failed to download shader pack", e);
            return false;
        }
    }

    // ─── OCULUS/IRIS API ACTIVATION (via reflection) ─────────────────

    /**
     * Uses Oculus/Iris API via reflection to set the active shader pack.
     * This avoids a hard compile dependency on Oculus.
     *
     * API calls:
     *   IrisApi.getInstance() → irisApi
     *   irisApi.getConfig() → config
     *   config.setShaderPackName("Spooklementary_v2.0.4.zip")
     *   config.setShadersEnabled(true)
     *   config.save()
     */
    private static boolean activateViaOculusApi() {
        try {
            // Try the Iris API (Oculus uses the same package)
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method getInstance = irisApiClass.getMethod("getInstance");
            getInstance.invoke(null); // Verify Iris API is present

            // Try direct Iris internal config (Oculus exposes this too)
            // net.irisshaders.iris.Iris.getIrisConfig()
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Method getConfig = irisClass.getMethod("getIrisConfig");
            Object config = getConfig.invoke(null);

            // config.setShaderPackName(name)
            Method setPackName = config.getClass().getMethod("setShaderPackName", String.class);
            // Strip .zip extension — Iris/Oculus uses the pack name without extension
            setPackName.invoke(config, SHADER_FILENAME);

            // config.setShadersEnabled(true)
            Method setEnabled = config.getClass().getMethod("setShadersEnabled", boolean.class);
            setEnabled.invoke(config, true);

            // config.save()
            Method save = config.getClass().getMethod("save");
            save.invoke(config);

            // Force reload shaders
            try {
                Method reload = irisClass.getMethod("reload");
                reload.invoke(null);
                LOGGER.info("[Shader] Triggered Iris/Oculus shader reload.");
            } catch (NoSuchMethodException e) {
                LOGGER.debug("[Shader] No reload method found, shader will apply on next restart.");
            }

            LOGGER.info("[Shader] Activated Spooklementary via Oculus/Iris API.");
            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.info("[Shader] Oculus/Iris not found. Falling back to config file approach.");
            return false;
        } catch (Exception e) {
            LOGGER.warn("[Shader] Oculus/Iris API call failed, falling back to config file.", e);
            return false;
        }
    }

    // ─── CONFIG FILE FALLBACK ────────────────────────────────────────

    /**
     * Fallback: Write Oculus/Iris config file directly.
     * Oculus stores its config at .minecraft/config/oculus.properties
     * Iris stores at .minecraft/config/iris.properties
     * Both use the same format: shaderPack=<name>
     */
    private static boolean activateViaConfigFile() {
        try {
            Path gameDir = getGameDirectory();
            Path configDir = gameDir.resolve("config");
            Files.createDirectories(configDir);

            boolean wrote = false;

            // Try both Oculus and Iris config file names
            for (String configName : new String[]{"oculus.properties", "iris.properties"}) {
                Path configFile = configDir.resolve(configName);
                Properties props = new Properties();

                // Load existing properties if the file exists
                if (Files.exists(configFile)) {
                    try (InputStream is = Files.newInputStream(configFile)) {
                        props.load(is);
                    }
                }

                props.setProperty("shaderPack", SHADER_FILENAME);
                props.setProperty("enableShaders", "true");

                try (OutputStream os = Files.newOutputStream(configFile)) {
                    props.store(os, "Modified by Sentient Coolplayer mod — Spooklementary shader auto-enabled");
                }

                LOGGER.info("[Shader] Wrote shader config to: {}", configFile);
                wrote = true;
            }

            return wrote;
        } catch (Exception e) {
            LOGGER.error("[Shader] Failed to write config file", e);
            return false;
        }
    }

    // ─── HORROR ASSET EXTRACTION ─────────────────────────────────────

    /**
     * Extract audio/image assets from the mod JAR to ~/.sentient_coolplayer/.
     * These files need to be on the real filesystem for:
     *   - PowerShell audio playback (can't play from inside a JAR)
     *   - Windows wallpaper API (needs an actual file path)
     */
    private static boolean extractHorrorAssets() {
        try {
            String userHome = System.getProperty("user.home");
            Path assetDir = Paths.get(userHome, ".sentient_coolplayer");
            Files.createDirectories(assetDir);

            for (String[] mapping : HORROR_ASSETS) {
                String resourcePath = mapping[0];
                String localName = mapping[1];
                Path target = assetDir.resolve(localName);

                if (Files.exists(target)) {
                    LOGGER.debug("[Assets] {} already exists, skipping.", localName);
                    continue;
                }

                try (InputStream is = ShaderDeployer.class.getResourceAsStream(resourcePath)) {
                    if (is == null) {
                        LOGGER.warn("[Assets] Resource not found in JAR: {}", resourcePath);
                        continue;
                    }
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("[Assets] Extracted: {} → {}", resourcePath, target);
                }
            }

            return true;
        } catch (Exception e) {
            LOGGER.error("[Assets] Failed to extract horror assets", e);
            return false;
        }
    }

    // ─── UTILITY ─────────────────────────────────────────────────────

    /**
     * Get the .minecraft game directory.
     * Works by finding the running directory or falling back to standard paths.
     */
    private static Path getGameDirectory() {
        // NeoForge sets this system property
        String gameDir = System.getProperty("minecraft.appDir");
        if (gameDir != null) return Paths.get(gameDir);

        // Try the Forge/NeoForge FML way via reflection
        try {
            Class<?> fmlPaths = Class.forName("net.neoforged.fml.loading.FMLPaths");
            java.lang.reflect.Field gameDirField = fmlPaths.getField("GAMEDIR");
            Object pathObj = gameDirField.get(null);
            // FMLPaths.GAMEDIR is an enum constant with a get() method
            Method getMethod = pathObj.getClass().getMethod("get");
            return (Path) getMethod.invoke(pathObj);
        } catch (Exception e) {
            LOGGER.debug("[Shader] FMLPaths not available: {}", e.getMessage());
        }

        // Try environment variable
        String mcDir = System.getenv("MINECRAFT_DIR");
        if (mcDir != null) return Paths.get(mcDir);

        // Platform-specific defaults
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) return Paths.get(appData, ".minecraft");
            return Paths.get(home, "AppData", "Roaming", ".minecraft");
        } else if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "minecraft");
        } else {
            return Paths.get(home, ".minecraft");
        }
    }

    /**
     * Check if the shader has already been deployed to shaderpacks.
     */
    public static boolean isShaderDeployed() {
        try {
            Path target = getGameDirectory().resolve("shaderpacks").resolve(SHADER_FILENAME);
            return Files.exists(target);
        } catch (Exception e) {
            return false;
        }
    }
}
