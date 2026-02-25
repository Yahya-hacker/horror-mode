package net.mcreator.insidethesystem.meta;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/**
 * Client-side screen events for the Sentient CoolPlayer mod.
 *
 * value = Dist.CLIENT ensures this class is NEVER loaded on a dedicated server,
 * preventing ClassNotFoundException for client-only imports.
 *
 * Responsibilities:
 *   1. Remove the "Quit Game" button from the Title Screen (no escape)
 *   2. Force the yellow splash text to "Run , NOW"
 *   3. Draw a custom disclaimer overlay on the WarningScreen
 *   4. Inject an "I'm ready" button that deploys + activates the Spooklementary shader
 *   5. Ensure shader is deployed even if player skips the warning screen
 */
@EventBusSubscriber(modid = "sentient_coolplayer", value = Dist.CLIENT)
public class ClientModEvents {
    private static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer-Client");

    /** Whether the player has already accepted the disclaimer this session. */
    private static volatile boolean disclaimerAccepted = false;

    /** Weak ref to last injected WarningScreen to avoid re-injection without leaking memory. */
    private static WeakReference<Screen> lastInjectedScreen = new WeakReference<>(null);

    /** Whether shader was deployed as a fallback (in case player bypasses the warning screen). */
    private static volatile boolean shaderDeployedFallback = false;

    /** Whether we already redirected to the ApiKeyScreen this session. */
    private static volatile boolean apiKeyScreenShown = false;

    // ─── Cached reflection fields for TitleScreen.splash ─────────────
    private static Field splashField = null;
    private static Field splashTextField = null; // SplashRenderer.splash (String)
    static {
        try {
            splashField = TitleScreen.class.getDeclaredField("splash");
            splashField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            for (Field f : TitleScreen.class.getDeclaredFields()) {
                if (f.getType() == SplashRenderer.class) {
                    f.setAccessible(true);
                    splashField = f;
                    break;
                }
            }
        }
        // Cache the SplashRenderer text field for render-time checks
        try {
            splashTextField = SplashRenderer.class.getDeclaredField("splash");
            splashTextField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            for (Field f : SplashRenderer.class.getDeclaredFields()) {
                if (f.getType() == String.class) {
                    f.setAccessible(true);
                    splashTextField = f;
                    break;
                }
            }
        }
    }

    // ─── TITLE SCREEN: Remove Quit Button + Force Splash ─────────────

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen titleScreen) {
            // 1) Remove Quit/Exit button — there is no escape
            for (var child : new java.util.ArrayList<>(event.getListenersList())) {
                if (child instanceof Button button) {
                    String msg = button.getMessage().getString().toLowerCase();
                    if (msg.contains("quit") || msg.contains("exit")) {
                        event.removeListener(button);
                        LOGGER.debug("[Client] Removed '{}' button from Title Screen.", msg);
                    }
                }
            }

            // 2) Force splash text to "Run , NOW" via reflection
            if (splashField != null) {
                try {
                    splashField.set(titleScreen, new SplashRenderer("Run , NOW"));
                    LOGGER.debug("[Client] Splash text set to 'Run , NOW'");
                } catch (Exception e) {
                    LOGGER.warn("[Client] Failed to override splash text", e);
                }
            }

            // 3) Fallback shader deployment — if the player somehow bypasses the
            //    WarningScreen (e.g., it was disabled), deploy the shader anyway
            //    the first time we see the TitleScreen.
            if (!shaderDeployedFallback && !ShaderDeployer.isShaderDeployed()) {
                shaderDeployedFallback = true;
                Thread deployThread = new Thread(() -> {
                    ShaderDeployer.deployAndActivate();
                    LOGGER.info("[Client] Shader deployed via TitleScreen fallback.");
                }, "SentientCoolplayer-ShaderFallback");
                deployThread.setDaemon(true);
                deployThread.start();
            }

            // 4) Show ApiKeyScreen on first TitleScreen if no API key configured
            if (!apiKeyScreenShown) {
                MetaOrchestrator orch = MetaOrchestrator.getInstance();
                if (orch != null && !orch.getAiBridge().hasApiKey()) {
                    apiKeyScreenShown = true;
                    // Defer to next tick so TitleScreen finishes init
                    Minecraft.getInstance().execute(() ->
                        Minecraft.getInstance().setScreen(new ApiKeyScreen(orch.getAiBridge()))
                    );
                }
            }

            // 5) Small "Entity Link" button for reconfiguring API key
            Button entityLinkBtn = Button.builder(
                    Component.literal("\u00A7a\u00A7l\u2699 Entity Link"),
                    btn -> {
                        MetaOrchestrator orch = MetaOrchestrator.getInstance();
                        if (orch != null) {
                            Minecraft.getInstance().setScreen(new ApiKeyScreen(orch.getAiBridge()));
                        }
                    }
            ).bounds(titleScreen.width - 110, titleScreen.height - 30, 100, 20).build();
            event.addListener(entityLinkBtn);
        }

        // 4) Inject "I'm ready" button on WarningScreen
        String screenName = event.getScreen().getClass().getSimpleName();
        if (screenName.equals("WarningScreen") && !disclaimerAccepted) {
            if (lastInjectedScreen.get() != event.getScreen()) {
                lastInjectedScreen = new WeakReference<>(event.getScreen());
                final Screen warningScreen = event.getScreen();

                int width = warningScreen.width;
                int height = warningScreen.height;

                int btnWidth = 200;
                int btnHeight = 20;
                int btnX = width / 2 - btnWidth / 2;
                int btnY = height / 2 + 80;

                Button readyButton = Button.builder(
                    Component.literal("\u00A7a\u00A7lI'm ready"),
                    (btn) -> {
                        LOGGER.info("[Client] Player accepted the disclaimer! Deploying shader...");
                        disclaimerAccepted = true;

                        Thread deployThread = new Thread(() -> {
                            boolean success = ShaderDeployer.deployAndActivate();
                            if (success) {
                                LOGGER.info("[Client] Shader deployed successfully.");
                            } else {
                                LOGGER.warn("[Client] Shader deployment had issues. Check logs.");
                            }
                        }, "SentientCoolplayer-ShaderDeploy");
                        deployThread.setDaemon(true);
                        deployThread.start();

                        // Click the original Proceed button to move past the warning
                        for (var child : new java.util.ArrayList<>(warningScreen.children())) {
                            if (child instanceof Button originalBtn) {
                                String msg = originalBtn.getMessage().getString().toLowerCase();
                                if (msg.contains("proceed") || msg.contains("continue") || msg.contains("ok")) {
                                    originalBtn.onPress();
                                    // After proceeding, check if we need the ApiKeyScreen
                                    Minecraft.getInstance().execute(() -> {
                                        MetaOrchestrator orch = MetaOrchestrator.getInstance();
                                        if (orch != null && !orch.getAiBridge().hasApiKey()) {
                                            apiKeyScreenShown = true;
                                            Minecraft.getInstance().setScreen(new ApiKeyScreen(orch.getAiBridge()));
                                        }
                                    });
                                    return;
                                }
                            }
                        }
                        // Fallback: show ApiKeyScreen if needed, else title
                        Minecraft.getInstance().execute(() -> {
                            MetaOrchestrator orch = MetaOrchestrator.getInstance();
                            if (orch != null && !orch.getAiBridge().hasApiKey()) {
                                apiKeyScreenShown = true;
                                Minecraft.getInstance().setScreen(new ApiKeyScreen(orch.getAiBridge()));
                            } else {
                                Minecraft.getInstance().setScreen(null);
                            }
                        });
                    }
                ).bounds(btnX, btnY, btnWidth, btnHeight).build();

                event.addListener(readyButton);
            }
        }
    }

    // ─── WARNING SCREEN: Custom Disclaimer Overlay ───────────────────

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        // Force splash text on every render of the TitleScreen (counteracts vanilla reloads)
        // Using cached reflection fields for zero per-frame allocation
        if (event.getScreen() instanceof TitleScreen titleScreen && splashField != null) {
            try {
                Object current = splashField.get(titleScreen);
                if (current instanceof SplashRenderer sr && splashTextField != null) {
                    String currentText = (String) splashTextField.get(sr);
                    if (!"Run , NOW".equals(currentText)) {
                        splashField.set(titleScreen, new SplashRenderer("Run , NOW"));
                    }
                } else if (current == null) {
                    splashField.set(titleScreen, new SplashRenderer("Run , NOW"));
                }
            } catch (Exception ignored) {}
        }

        // Disclaimer overlay on WarningScreen
        String screenName = event.getScreen().getClass().getSimpleName();
        if (screenName.equals("WarningScreen") && !disclaimerAccepted) {
            int width = event.getScreen().width;
            int height = event.getScreen().height;

            // Dark overlay covering the original warning text
            event.getGuiGraphics().fill(0, 0, width, height, 0xDD000000);

            String[] lines = {
                "\u00A7c\u00A7l\u2588 SENTIENT COOLPLAYER \u2588\u00A7r",
                "",
                "\u00A76This mod contains intense psychological horror elements.\u00A7r",
                "It will interact with your \u00A7eWindows desktop\u00A7r, play sounds",
                "\u00A7ebypassing Minecraft's volume\u00A7r, and read process names.",
                "",
                "\u00A7eWHAT IT DOES:\u00A7r",
                " \u2022 Installs the \u00A7bSpooklementary\u00A7r horror shader automatically",
                " \u2022 Changes your wallpaper temporarily during key moments",
                " \u2022 Plays audio outside Minecraft (through system speakers)",
                " \u2022 Drops narrative text files on your Desktop",
                " \u2022 Shows a brief fake overlay and fake BSOD effect",
                " \u2022 Spawns a harmless background process (narrative element)",
                "",
                "\u00A7aIT IS 100% SAFE.\u00A7r No data is sent except Gemini AI chat.",
                "No files are deleted. Everything is reversible.",
                "",
                "\u00A7d\u00A7lClick the button below to accept and begin.\u00A7r"
            };

            int y = height / 2 - (lines.length * 12) / 2 - 30;
            Font font = Minecraft.getInstance().font;
            for (String line : lines) {
                int textWidth = font.width(line);
                event.getGuiGraphics().drawString(font, line, width / 2 - textWidth / 2, y, 0xFFFFFF);
                y += 12;
            }
        }
    }

    /**
     * Returns whether the disclaimer has been accepted this session.
     */
    public static boolean isDisclaimerAccepted() {
        return disclaimerAccepted;
    }
}
