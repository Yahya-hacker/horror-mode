package net.mcreator.insidethesystem.meta;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client-side screen events for the Sentient CoolPlayer mod.
 *
 * value = Dist.CLIENT ensures this class is NEVER loaded on a dedicated server,
 * preventing ClassNotFoundException for client-only imports.
 *
 * Responsibilities:
 *   1. Remove the "Quit Game" button from the Title Screen (horror immersion)
 *   2. Draw a custom disclaimer overlay on the WarningScreen
 *   3. Inject an "I'm ready" button that deploys + activates the Spooklementary shader
 */
@EventBusSubscriber(modid = "sentient_coolplayer", value = Dist.CLIENT)
public class ClientModEvents {
    private static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer-Client");

    /** Whether the player has already accepted the disclaimer this session. */
    private static volatile boolean disclaimerAccepted = false;

    /** Whether we already injected the "I'm ready" button on the current WarningScreen instance. */
    private static Screen lastInjectedScreen = null;

    // ─── TITLE SCREEN: Remove Quit Button ────────────────────────────

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // 1) Remove Quit button from TitleScreen
        if (event.getScreen() instanceof TitleScreen) {
            for (var child : new java.util.ArrayList<>(event.getListenersList())) {
                if (child instanceof Button button) {
                    String msg = button.getMessage().getString().toLowerCase();
                    if (msg.contains("quit") || msg.contains("exit")) {
                        button.visible = false;
                        button.active = false;
                    }
                }
            }
        }

        // 2) Inject "I'm ready" button on WarningScreen
        String screenName = event.getScreen().getClass().getSimpleName();
        if (screenName.equals("WarningScreen") && !disclaimerAccepted) {
            if (lastInjectedScreen != event.getScreen()) {
                lastInjectedScreen = event.getScreen();
                final Screen warningScreen = event.getScreen();

                int width = warningScreen.width;
                int height = warningScreen.height;

                // Calculate button position below our disclaimer text
                int btnWidth = 200;
                int btnHeight = 20;
                int btnX = width / 2 - btnWidth / 2;
                int btnY = height / 2 + 80;

                Button readyButton = Button.builder(
                    Component.literal("\u00A7a\u00A7lI'm ready"),
                    (btn) -> {
                        LOGGER.info("[Client] Player accepted the disclaimer! Deploying shader...");
                        disclaimerAccepted = true;

                        // Deploy shader + extract horror assets on a background thread
                        // to avoid freezing the UI
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

                        // Proceed to the next screen (simulate clicking the original "Proceed" button)
                        // Find and click the original proceed button
                        for (var child : new java.util.ArrayList<>(warningScreen.children())) {
                            if (child instanceof Button originalBtn) {
                                String msg = originalBtn.getMessage().getString().toLowerCase();
                                if (msg.contains("proceed") || msg.contains("continue") || msg.contains("ok")) {
                                    originalBtn.onPress();
                                    return;
                                }
                            }
                        }
                        // Fallback: just close the screen and go to title
                        Minecraft.getInstance().setScreen(null);
                    }
                ).bounds(btnX, btnY, btnWidth, btnHeight).build();

                event.addListener(readyButton);
            }
        }
    }

    // ─── WARNING SCREEN: Custom Disclaimer Overlay ───────────────────

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
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
     * Used by MetaOrchestrator to know when to start full horror features.
     */
    public static boolean isDisclaimerAccepted() {
        return disclaimerAccepted;
    }
}
