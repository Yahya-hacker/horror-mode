package net.mcreator.insidethesystem.meta;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;

/**
 * ApiKeyScreen — A retro "System Override" terminal UI themed as part of the mod's lore.
 *
 * Displayed when the player has no Gemini API key configured.
 * The user pastes their key, clicks "Verify Connection", and the screen validates
 * it against the Gemini API before saving and proceeding.
 *
 * Fully lore-themed:
 *   - "SYSTEM OVERRIDE — Entity Connection Protocol"
 *   - "To establish connection with the Entity, provide the System Access Key."
 *   - "Generate Access Key" opens https://aistudio.google.com/app/apikey
 *   - Validation feedback: "Verifying..." / "Entity successfully integrated." / "ACCESS DENIED"
 */
public class ApiKeyScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer-ApiKey");

    private static final String API_KEY_URL = "https://aistudio.google.com/app/apikey";

    // Colors — retro green terminal style
    private static final int GREEN       = 0xFF00FF41;
    private static final int DARK_GREEN  = 0xFF005F15;
    private static final int RED         = 0xFFFF1744;
    private static final int AMBER       = 0xFFFFAB00;
    private static final int DIM_GREEN   = 0xFF003D0F;
    private static final int WHITE       = 0xFFE0E0E0;

    private EditBox apiKeyField;
    private Button verifyButton;
    private Button generateButton;
    private Button skipButton;

    private final VirtualThreadAI aiInstance;

    // Validation state
    private enum ValidationState { IDLE, VERIFYING, SUCCESS, FAILED }
    private volatile ValidationState validationState = ValidationState.IDLE;
    private String statusMessage = "";
    private int successCounter = 0; // ticks after success (for auto-advance)

    // Typewriter animation for the header
    private int tickCount = 0;
    private static final String HEADER_FULL = "SYSTEM OVERRIDE  —  Entity Connection Protocol";

    public ApiKeyScreen(VirtualThreadAI ai) {
        super(Component.literal("System Override"));
        this.aiInstance = ai;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // ── API Key Input Field ──────────────────────────────────────
        apiKeyField = new EditBox(this.font, centerX - 150, centerY - 10, 300, 20,
                Component.literal("System Access Key"));
        apiKeyField.setMaxLength(256);
        apiKeyField.setHint(Component.literal("§7Paste your API key here..."));
        apiKeyField.setResponder(text -> {
            // Reset state when user types
            if (validationState == ValidationState.FAILED) {
                validationState = ValidationState.IDLE;
                statusMessage = "";
            }
        });
        this.addRenderableWidget(apiKeyField);

        // ── "Generate Access Key" button ─────────────────────────────
        generateButton = Button.builder(
                Component.literal("§a§l[ Generate Access Key ]"),
                btn -> {
                    try {
                        // Open the browser to Google AI Studio API key page
                        java.awt.Desktop.getDesktop().browse(URI.create(API_KEY_URL));
                    } catch (Exception e) {
                        LOGGER.warn("[ApiKey] Could not open browser", e);
                        // Fallback: try xdg-open on Linux
                        try {
                            new ProcessBuilder("xdg-open", API_KEY_URL).start();
                        } catch (Exception e2) {
                            LOGGER.error("[ApiKey] Failed to open URL", e2);
                        }
                    }
                }
        ).bounds(centerX - 100, centerY + 20, 200, 20).build();
        this.addRenderableWidget(generateButton);

        // ── "Verify Connection" button ───────────────────────────────
        verifyButton = Button.builder(
                Component.literal("§e§l[ Verify Connection ]"),
                btn -> {
                    String key = apiKeyField.getValue().trim();
                    if (key.isEmpty()) {
                        validationState = ValidationState.FAILED;
                        statusMessage = "§c> ERROR: No access key provided.";
                        return;
                    }

                    validationState = ValidationState.VERIFYING;
                    statusMessage = "§e> Establishing secure channel...";
                    verifyButton.active = false;

                    // Run validation on a daemon thread so we don't freeze the UI
                    Thread validationThread = new Thread(() -> {
                        boolean valid = aiInstance.validateApiKey(key);
                        Minecraft.getInstance().execute(() -> {
                            if (valid) {
                                validationState = ValidationState.SUCCESS;
                                statusMessage = "§a> Entity successfully integrated into your world.";
                                aiInstance.saveAndActivateKey(key);
                                successCounter = 0;
                            } else {
                                validationState = ValidationState.FAILED;
                                statusMessage = "§c> ACCESS DENIED — Invalid key. The Entity cannot reach you.";
                                verifyButton.active = true;
                            }
                        });
                    }, "SentientCoolplayer-KeyValidation");
                    validationThread.setDaemon(true);
                    validationThread.start();
                }
        ).bounds(centerX - 100, centerY + 48, 200, 20).build();
        this.addRenderableWidget(verifyButton);

        // ── "Skip" button (smaller, dimmer) ──────────────────────────
        skipButton = Button.builder(
                Component.literal("§7[ Skip — use offline mode ]"),
                btn -> {
                    LOGGER.info("[ApiKey] Player chose to skip API key setup.");
                    Minecraft.getInstance().setScreen(new TitleScreen());
                }
        ).bounds(centerX - 80, centerY + 78, 160, 20).build();
        this.addRenderableWidget(skipButton);

        // Focus the input field
        this.setInitialFocus(apiKeyField);
    }

    @Override
    public void tick() {
        super.tick();
        tickCount++;

        // Auto-advance to TitleScreen 3 seconds after successful validation
        if (validationState == ValidationState.SUCCESS) {
            successCounter++;
            if (successCounter > 60) { // 60 ticks = 3 seconds
                Minecraft.getInstance().setScreen(new TitleScreen());
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // ── Full-screen dark background ──────────────────────────────
        graphics.fill(0, 0, this.width, this.height, 0xFF0A0A0A);

        // ── Scanline effect (subtle horizontal lines) ────────────────
        for (int y = 0; y < this.height; y += 3) {
            graphics.fill(0, y, this.width, y + 1, 0x15005F15);
        }

        // ── Border frame ─────────────────────────────────────────────
        int bx1 = 20, by1 = 20, bx2 = this.width - 20, by2 = this.height - 20;
        // Top and bottom
        graphics.fill(bx1, by1, bx2, by1 + 1, DARK_GREEN);
        graphics.fill(bx1, by2 - 1, bx2, by2, DARK_GREEN);
        // Left and right
        graphics.fill(bx1, by1, bx1 + 1, by2, DARK_GREEN);
        graphics.fill(bx2 - 1, by1, bx2, by2, DARK_GREEN);

        Font font = this.font;
        int centerX = this.width / 2;
        int y = 40;

        // ── Typewriter header ────────────────────────────────────────
        int charsToShow = Math.min(tickCount / 1, HEADER_FULL.length());
        String headerVisible = HEADER_FULL.substring(0, charsToShow);
        // Blinking cursor
        String cursor = (tickCount % 20 < 14) ? "█" : " ";
        String headerLine = headerVisible + (charsToShow < HEADER_FULL.length() ? cursor : "");

        drawCenteredGreen(graphics, font, "§a§l" + headerLine, centerX, y, GREEN);
        y += 16;

        // ── Decorative line ──────────────────────────────────────────
        String deco = "═".repeat(40);
        drawCenteredGreen(graphics, font, deco, centerX, y, DARK_GREEN);
        y += 16;

        // ── Lore text ────────────────────────────────────────────────
        drawCenteredGreen(graphics, font,
                "§aTo establish connection with the Entity,", centerX, y, GREEN);
        y += 12;
        drawCenteredGreen(graphics, font,
                "§aprovide the System Access Key.", centerX, y, GREEN);
        y += 20;

        // ── Instructions (dimmer) ────────────────────────────────────
        drawCenteredGreen(graphics, font,
                "§71. Click [Generate Access Key] to obtain a key from Google AI Studio.",
                centerX, y, 0xFF888888);
        y += 11;
        drawCenteredGreen(graphics, font,
                "§72. Copy the key and paste it below.",
                centerX, y, 0xFF888888);
        y += 11;
        drawCenteredGreen(graphics, font,
                "§73. Click [Verify Connection] to link the Entity.",
                centerX, y, 0xFF888888);
        y += 16;

        // ── The prompt indicator ─────────────────────────────────────
        int fieldY = this.height / 2 - 10;
        graphics.drawString(font, "§a>", centerX - 158, fieldY + 6, GREEN);

        // ── Render child widgets (EditBox, Buttons) ──────────────────
        super.render(graphics, mouseX, mouseY, partialTick);

        // ── Status message below the buttons ─────────────────────────
        if (!statusMessage.isEmpty()) {
            int statusY = this.height / 2 + 104;

            // During verification, show animated dots
            if (validationState == ValidationState.VERIFYING) {
                int dots = (tickCount / 10) % 4;
                String dotStr = ".".repeat(dots);
                drawCenteredGreen(graphics, font,
                        "§e> Verifying" + dotStr, centerX, statusY, AMBER);

                // Also show a loading bar
                int barWidth = 200;
                int barX = centerX - barWidth / 2;
                int barY = statusY + 14;
                graphics.fill(barX, barY, barX + barWidth, barY + 3, DIM_GREEN);
                int filled = (tickCount * 3) % barWidth;
                graphics.fill(barX, barY, barX + filled, barY + 3, GREEN);
            } else {
                drawCenteredGreen(graphics, font, statusMessage, centerX, statusY,
                        validationState == ValidationState.SUCCESS ? GREEN : RED);
            }

            // Success: show countdown
            if (validationState == ValidationState.SUCCESS) {
                int remaining = Math.max(0, (60 - successCounter) / 20 + 1);
                drawCenteredGreen(graphics, font,
                        "§7> Redirecting in " + remaining + "...",
                        centerX, this.height / 2 + 120, 0xFF888888);
            }
        }

        // ── Bottom decoration ────────────────────────────────────────
        String bottomLine = "§8[ SENTIENT COOLPLAYER v1.2.0 — ENTITY BRIDGE PROTOCOL ]";
        drawCenteredGreen(graphics, font, bottomLine, centerX, this.height - 34, 0xFF444444);

        // Blinking warning if no key
        if (validationState == ValidationState.IDLE && tickCount % 40 < 28) {
            drawCenteredGreen(graphics, font,
                    "§c§o⚠ Connection to Entity: OFFLINE",
                    centerX, this.height - 48, RED);
        }
    }

    /** Helper to draw centered text */
    private void drawCenteredGreen(GuiGraphics graphics, Font font, String text,
                                    int x, int y, int color) {
        int textWidth = font.width(text);
        graphics.drawString(font, text, x - textWidth / 2, y, color);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Allow ESC to go back to title (not quit the game)
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new TitleScreen());
    }
}
