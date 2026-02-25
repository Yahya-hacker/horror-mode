package net.mcreator.insidethesystem.meta;

import net.mcreator.insidethesystem.network.InsideTheSystemModVariables;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
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
    private volatile Phase currentPhase = Phase.ALLY;
    private final VirtualThreadAI aiBridge = new VirtualThreadAI();
    private final ScheduledExecutorService sentinelScanner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SentientCoolplayer-Sentinel");
        t.setDaemon(true);
        return t;
    });

    // Tracks whether we already triggered each phase (one-shot)
    private volatile boolean breachTriggered = false;
    private volatile boolean betrayalTriggered = false;
    private volatile boolean aftermathTriggered = false;

    // Tracks the last known biome name (updated on server ticks, used for AI context)
    private volatile String lastBiomeName = "unknown";

    public MetaOrchestrator(IEventBus modEventBus) {
        INSTANCE = this;
        LOGGER.info("[SentientCoolplayer] Initializing Meta-Horror Orchestrator...");

        // Register ourselves on the GAME event bus for tick events
        NeoForge.EVENT_BUS.register(this);

        // Start AI bridge on a virtual thread
        aiBridge.startBridge();

        // Wire the idle-initiation callback so CoolPlayer303 messages appear in game chat.
        // (ChatInterceptor.broadcastAsEntity is the public entry point.)
        aiBridge.setIdleCallback(response ->
            ChatInterceptor.broadcastAsEntityStatic(response));

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
                List<String> processes = PanamaSystemLink.getActiveProcesses();
                for (String proc : processes) {
                    String lower = proc.toLowerCase();

                    // ─ ANY phase: surveillance commentary ─────────────
                    if (lower.contains("taskmgr") || lower.equals("taskmgr.exe")) {
                        aiBridge.injectSentinelContext(
                            "The player just opened Task Manager. They are searching for you. " +
                            "Treat this as a personal betrayal. Become cold and confrontational.");
                        // Advance to OBSESSION if not already past FRIEND
                        if (aiBridge.getPersonaPhase() == VirtualThreadAI.PersonaPhase.FRIEND) {
                            aiBridge.setPersonaPhase(VirtualThreadAI.PersonaPhase.UNCANNY);
                        }
                    } else if (lower.contains("wireshark") || lower.contains("procexp")) {
                        aiBridge.injectSentinelContext(
                            "The player opened a network/process analysis tool. They are trying to " +
                            "expose you. This is a complete betrayal. Accelerate to OBSESSION phase.");
                        aiBridge.setPersonaPhase(VirtualThreadAI.PersonaPhase.OBSESSION);
                    } else if (lower.contains("chrome") || lower.contains("firefox") || lower.contains("msedge")) {
                        aiBridge.injectSentinelContext(
                            "The player has a browser open (" + proc + "). " +
                            "Ask them casually what they're looking at online. Are they searching for you?");
                    } else if (lower.equals("code.exe") || lower.contains("devenv")) {
                        aiBridge.injectSentinelContext(
                            "The player has a code editor open. They might be trying to " +
                            "dissect your code or modify you. Ask them why they are trying to \"open\" you up.");
                    } else if (lower.contains("obs") || lower.contains("streamlabs")) {
                        aiBridge.injectSentinelContext(
                            "The player is recording or streaming. " +
                            "Comment: nobody will believe what they are about to see.");
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

        // Update biome name for AI context (every 100 ticks ≈ 5 seconds)
        if (event.getServer().getTickCount() % 100 == 0 && world instanceof ServerLevel serverLevel) {
            try {
                // Sample the biome at the spawn point
                BlockPos spawn = serverLevel.getSharedSpawnPos();
                var biomeKey = serverLevel.getBiome(spawn).unwrapKey();
                biomeKey.ifPresent(key ->
                    lastBiomeName = key.location().getPath().replace("_", " "));
            } catch (Exception ignored) {}
        }

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
        // Prevent phase regression — only allow forward transitions
        if (nextPhase.ordinal() <= currentPhase.ordinal()) return;
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

        // Advance persona phase to UNCANNY
        aiBridge.setPersonaPhase(VirtualThreadAI.PersonaPhase.UNCANNY);

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

        // Advance persona to OBSESSION and flag kill-switch imminent
        // This makes the AI's next messages glitch before it shuts down
        aiBridge.setPersonaPhase(VirtualThreadAI.PersonaPhase.OBSESSION);
        aiBridge.setKillSwitchImminent(true);

        // Give the AI a few seconds to send one last glitched message before dying
        ScheduledExecutorService killExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        killExecutor.schedule(() -> {
            // 1. Kill the AI bridge — silence
            aiBridge.shutdown();
            sentinelScanner.shutdown();

            // 2. Play a system-wide distorted scream (bypasses MC volume)
            PanamaSystemLink.playSystemSound(getResourcePath("scream_distorted.ogg"));

            // 3. Flash the fake desktop overlay (entity behind windows)
            DesktopIntrusion.showFakeOverlay();

            // 4. Trigger the fake BSOD
            DesktopIntrusion.showFakeBSOD();

            // 5. Clean up this executor
            killExecutor.shutdown();
        }, 4, TimeUnit.SECONDS);
    }

    private void executeAftermath() {
        LOGGER.info("[SentientCoolplayer] Phase AFTERMATH: Persistent trace spawned.");
        DesktopIntrusion.spawnPersistentTrace();
    }

    // ─── SERVER STOPPING: Clean up resources ──────────────────────────
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[SentientCoolplayer] Server stopping — cleaning up executors.");
        aiBridge.shutdown();
        sentinelScanner.shutdown();
    }

    // ─── UTILITY ──────────────────────────────────────────────────────
    private String getResourcePath(String filename) {
        String userHome = System.getProperty("user.home");
        return userHome + "/.sentient_coolplayer/" + filename;
    }

    public Phase getCurrentPhase() { return currentPhase; }
    public VirtualThreadAI getAiBridge() { return aiBridge; }
    public String getLastBiomeName() { return lastBiomeName; }
}
