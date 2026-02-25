package net.mcreator.insidethesystem.meta;

import net.mcreator.insidethesystem.entity.CoolPlayer303Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * ChatInterceptor — Hooks into NeoForge's ServerChatEvent to intercept player messages
 * and route them through the Gemini AI bridge.
 *
 * PRIVACY: Only the text typed in the MC chat bar is sent to Gemini,
 * along with the biome name and running process names.
 */
@EventBusSubscriber(modid = "sentient_coolplayer")
public class ChatInterceptor {
    private static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer-Chat");

    /** Last known server instance for idle-initiation broadcasts. */
    private static volatile MinecraftServer lastServer = null;

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        MetaOrchestrator orchestrator = MetaOrchestrator.getInstance();
        if (orchestrator == null) return;

        // Route to AI during ALLY and BREACH phases
        MetaOrchestrator.Phase phase = orchestrator.getCurrentPhase();
        if (phase == MetaOrchestrator.Phase.BETRAYAL || phase == MetaOrchestrator.Phase.AFTERMATH) return;

        String playerName = event.getPlayer().getName().getString();
        String rawMessage = event.getRawText();
        ServerLevel world = (ServerLevel) event.getPlayer().level();
        lastServer = world.getServer();

        // Check if a CoolPlayer303Entity is nearby (within 64 blocks)
        List<CoolPlayer303Entity> npcs = world.getEntitiesOfClass(
                CoolPlayer303Entity.class,
                new AABB(
                        event.getPlayer().getX() - 64, event.getPlayer().getY() - 64, event.getPlayer().getZ() - 64,
                        event.getPlayer().getX() + 64, event.getPlayer().getY() + 64, event.getPlayer().getZ() + 64
                )
        );

        if (npcs.isEmpty()) return;

        LOGGER.info("[Chat] Intercepted from {}: '{}'", playerName, rawMessage);

        // Gather context for the AI
        String biomeName = orchestrator.getLastBiomeName();
        // Process list gathered inside the virtual thread to avoid blocking the server thread

        // Route to Gemini via virtual thread
        orchestrator.getAiBridge().processChatAsync(
                rawMessage, playerName, biomeName, null,
                response -> broadcastAsEntity(world.getServer(), response)
        );
    }

    /**
     * Broadcasts an AI response into the game chat as CoolPlayer303.
     * Called both by the chat event handler and by the idle-initiation timer.
     */
    private static void broadcastAsEntity(MinecraftServer server, String response) {
        if (server == null) return;
        server.execute(() ->
            server.getPlayerList().broadcastSystemMessage(
                Component.literal("§d<CoolPlayer303>§r " + response),
                false
            )
        );
    }

    /**
     * Static entry point for the idle-initiation callback registered in MetaOrchestrator.
     * Picks up the last known server to route the message.
     */
    public static void broadcastAsEntityStatic(String response) {
        MinecraftServer server = lastServer;
        if (server == null) {
            server = ServerLifecycleHooks.getCurrentServer();
        }
        broadcastAsEntity(server, response);
    }
}
