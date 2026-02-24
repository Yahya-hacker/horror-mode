package net.mcreator.insidethesystem.meta;

import net.mcreator.insidethesystem.entity.CoolPlayer303Entity;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * ChatInterceptor — Hooks into NeoForge's ServerChatEvent to intercept player messages
 * and route them through the Gemini AI bridge.
 *
 * Also feeds biome context to the AI for the system prompt's {minecraft_biome} variable.
 *
 * PRIVACY: Only the text typed in the MC chat bar is sent to Gemini.
 */
@EventBusSubscriber(modid = "sentient_coolplayer")
public class ChatInterceptor {
    private static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer-Chat");

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        MetaOrchestrator orchestrator = MetaOrchestrator.getInstance();
        if (orchestrator == null) return;

        // Intercept during ALLY and BREACH phases (AI goes silent during BETRAYAL)
        MetaOrchestrator.Phase phase = orchestrator.getCurrentPhase();
        if (phase == MetaOrchestrator.Phase.BETRAYAL || phase == MetaOrchestrator.Phase.AFTERMATH) return;

        String playerName = event.getPlayer().getName().getString();
        String rawMessage = event.getRawText();
        ServerLevel world = (ServerLevel) event.getPlayer().level();

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

        // Route to Gemini via virtual thread
        orchestrator.getAiBridge().processChatAsync(rawMessage, playerName, response -> {
            // Send the AI response back as a system message (as CoolPlayer303)
            if (world.getServer() != null) {
                world.getServer().execute(() -> {
                    world.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal("§d<CoolPlayer303>§r " + response),
                            false
                    );
                });
            }
        });
    }
}
