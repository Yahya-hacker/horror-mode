package net.mcreator.insidethesystem.meta;

import net.mcreator.insidethesystem.network.InsideTheSystemModVariables;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.level.LevelAccessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MetaOrchestrator — The central brain of the Sentient Coolplayer mod.
 *
 * Reads the REAL state flags from the original "Inside the System" mod's
 * {@link InsideTheSystemModVariables.MapVariables} to transition between
 * psychological phases seamlessly.
 *
 * State mapping from the original mod:
 *   - MapVariables.Angry == false  → Phase ALLY  (CoolPlayer is friendly)
 *   - MapVariables.Angry == true   → Phase BREACH (tension building)
 *   - MapVariables.eventfollover == true → Phase BETRAYAL (AngryCoolPlayer active, the hunt)
 *   - MapVariables.GameFinished == true  → Phase AFTERMATH (post-game persistent trace)
 */
@Mod("sentient_coolplayer")
public class MetaOrchestrator {
    public static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer");

    public enum Phase {
        ALLY,           // Friendly AI companion, process scanning, gaslighting
        BREACH,         // Desktop intrusion, wallpaper swap, ghost files, whispers
        BETRAYAL,       // Kill-switch: AI dies, fake BSOD, overlay, scream
        AFTERMATH       // Persistent background trace after game close
    }

    private static MetaOrchestrator INSTANCE;
    private Phase currentPhase = Phase.ALLY;
    private final VirtualThreadAI aiBridge = new VirtualThreadAI();
    private final ScheduledExecutorService sentinelScanner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SentientCoolplayer-Sentinel");
        t.setDaemon(true);
        return t;
    });

    // Tracks whether we already triggered each phase (one-shot)
    private boolean breachTriggered = false;
    private boolean betrayalTriggered = false;
    private boolean aftermathTriggered = false;

    public MetaOrchestrator(IEventBus modEventBus) {
        INSTANCE = this;
        LOGGER.info("[SentientCoolplayer] Initializing Meta-Horror Orchestrator...");

        // Register ourselves on the GAME event bus for tick events
        NeoForge.EVENT_BUS.register(this);

        // Start AI bridge on a virtual thread
        aiBridge.startBridge();

        // Start the system sentinel (process scanner)
        startSentinel();
    }

    public static MetaOrchestrator getInstance() {
        return INSTANCE;
    }

    // ─── SENTINEL: Scans host processes every 30s ──────────────────────
    private void startSentinel() {
        sentinelScanner.scheduleAtFixedRate(() -> {
            try {
                if (currentPhase != Phase.ALLY) return;

                List<String> processes = PanamaSystemLink.getActiveProcesses();
                for (String proc : processes) {
                    String lower = proc.toLowerCase();
                    if (lower.contains("chrome") || lower.contains("firefox") || lower.contains("edge")) {
                        aiBridge.injectSentinelContext("I see you opened " + proc + ". Looking for answers about me?");
                    } else if (lower.contains("taskmgr") || lower.contains("task manager")) {
                        aiBridge.injectSentinelContext("Task Manager? Are you trying to find me... or kill me?");
                    } else if (lower.contains("obs") || lower.contains("streamlabs")) {
                        aiBridge.injectSentinelContext("Recording me? Cute. Nobody will believe you.");
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("[SentientCoolplayer] Sentinel scan error", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    // ─── SERVER TICK: Read original mod state and orchestrate ──────────
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        LevelAccessor world = event.getServer().overworld();

        InsideTheSystemModVariables.MapVariables vars =
                InsideTheSystemModVariables.MapVariables.get(world);

        if (vars == null) return;

        // Phase detection from the original mod's state machine
        if (vars.eventfollover && !betrayalTriggered) {
            // AngryCoolPlayer303 is active and hunting — THE BETRAYAL
            triggerPhaseChange(Phase.BETRAYAL);
            betrayalTriggered = true;
        } else if (vars.Angry && !breachTriggered) {
            // CoolPlayer turned hostile — THE BREACH begins
            triggerPhaseChange(Phase.BREACH);
            breachTriggered = true;
        }

        if (vars.GameFinished && !aftermathTriggered) {
            triggerPhaseChange(Phase.AFTERMATH);
            aftermathTriggered = true;
        }
    }

    // ─── PHASE TRANSITIONS ────────────────────────────────────────────
    public void triggerPhaseChange(Phase nextPhase) {
        if (nextPhase == currentPhase) return;
        LOGGER.info("[SentientCoolplayer] ═══ PHASE TRANSITION: {} → {} ═══", currentPhase, nextPhase);
        this.currentPhase = nextPhase;

        switch (nextPhase) {
            case BREACH -> executeBreach();
            case BETRAYAL -> executeBetrayal();
            case AFTERMATH -> executeAftermath();
            default -> {}
        }
    }

    private void executeBreach() {
        LOGGER.info("[SentientCoolplayer] Phase BREACH: Desktop intrusion begins.");

        // Change wallpaper (Windows only, safe no-op on Linux)
        // Uses "thank you.jpg" from the original mod, extracted by ShaderDeployer
        PanamaSystemLink.setWallpaper(getResourcePath("horror_wallpaper.jpg"));

        // Play a creepy whisper through the system audio, bypassing MC volume
        // Uses the suspenseful ping sound from the original mod
        PanamaSystemLink.playSystemSound(getResourcePath("whisper.ogg"));

        // Drop a ghost file on the desktop
        DesktopIntrusion.dropGhostLog("coolplayer_message.txt",
                "I know what you did.\n" +
                "You thought closing the game would save you.\n" +
                "I live in your files now.\n\n" +
                "    — CoolPlayer303");

        // Schedule the microphone echo (record 3s, play back after 60s)
        MicrophoneEcho.scheduleEcho();
    }

    private void executeBetrayal() {
        LOGGER.info("[SentientCoolplayer] ██ THE KILL-SWITCH HAS BEEN ACTIVATED ██");

        // 1. Kill the AI bridge — silence
        aiBridge.shutdown();
        sentinelScanner.shutdown();

        // 2. Play a system-wide distorted scream (bypasses MC volume)
        // Uses the jumpscare screamer sound from the original mod
        PanamaSystemLink.playSystemSound(getResourcePath("scream_distorted.ogg"));

        // 3. Flash the fake desktop overlay (entity behind windows)
        DesktopIntrusion.showFakeOverlay();

        // 4. Trigger the fake BSOD
        DesktopIntrusion.showFakeBSOD();
    }

    private void executeAftermath() {
        LOGGER.info("[SentientCoolplayer] Phase AFTERMATH: Persistent trace spawned.");
        DesktopIntrusion.spawnPersistentTrace();
    }

    // ─── UTILITY ──────────────────────────────────────────────────────
    private String getResourcePath(String filename) {
        // Resolve from the mod's resource directory or a known path
        String userHome = System.getProperty("user.home");
        return userHome + "/.sentient_coolplayer/" + filename;
    }

    public Phase getCurrentPhase() { return currentPhase; }
    public VirtualThreadAI getAiBridge() { return aiBridge; }
}
