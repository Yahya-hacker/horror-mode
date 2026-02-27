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
import java.util.Set;

/**
 * ChatInterceptor — Hooks into NeoForge's ServerChatEvent to intercept player messages
 * and route them through the Gemini AI bridge.
 *
 * Script-aware: Messages that match ITS's built-in dialogue triggers are NOT sent
 * to Gemini (ITS already handles them). Only novel/unscripted messages go to the AI.
 *
 * PRIVACY: Only the text typed in the MC chat bar is sent to Gemini,
 * along with the biome name and running process names.
 */
@EventBusSubscriber(modid = "sentient_coolplayer")
public class ChatInterceptor {
    private static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer-Chat");

    /** Last known server instance for idle-initiation broadcasts. */
    private static volatile MinecraftServer lastServer = null;

    // ─── ITS SCRIPT TRIGGER PHRASES ──────────────────────────────────
    // These are ALL the trigger phrases from CoolPlayerResponseProcedureProcedure.
    // If the player's message contains any of these, ITS will handle the response,
    // so we should NOT waste a Gemini API call on it.
    private static final Set<String> ITS_TRIGGER_PHRASES = Set.of(
        // --- Identity questions ---
        "who are you", "кто ты", "あなたは誰",
        "what are you", "что ты", "あなたは何？",
        // --- Help/Real/See ---
        "help me", "помоги", "助けて",
        "are you real", "это сон", "これは夢？",
        "do you see", "ты видел это", "見えた？",
        // --- Location/Glitch/Shadow ---
        "where am i", "где я", "私はどこ？",
        "glitch", "глюк", "バグ",
        "shadow", "тень", "影",
        // --- Follow/Wake/Alive ---
        "follow", "следуй", "ついてきて",
        "wake up", "проснись",
        "alive", "жив", "まだ生きてる？",
        // --- Greetings ---
        "hello", "hi", "привет", "こんにちは",
        "bye", "пока", "さようなら",
        // --- Story/Lore ---
        "what happened", "что случилось", "何が起きた",
        "why", "почему", "なぜ",
        "herobrine", "хиробрин",
        "entity 303", "энтити 303",
        // --- Death/Save/Error ---
        "death", "смерть",
        "kill me", "убей меня",
        "save me", "спаси меня",
        "error", "ошибка", "エラー",
        // --- Russian profanity (triggers HUY_RESPONSE) ---
        "хуй", "huy",
        // --- English profanity (triggers INSULT_RESPONSE) ---
        "fuck", "shit", "bitch", "asshole",
        "idiot", "moron", "retard",
        // --- Russian profanity (triggers INSULT_RESPONSE) ---
        "сука", "блять", "ебать", "нахуй", "пиздец", "гандон", "урод",
        "тупой", "дебил", "идиот", "даун"
    );

    /**
     * Checks if a player message matches any ITS scripted trigger phrase.
     * ITS uses String.contains() matching on the lowercased message.
     */
    private static boolean isHandledByITSScript(String rawMessage) {
        String lower = rawMessage.toLowerCase().trim();
        for (String trigger : ITS_TRIGGER_PHRASES) {
            if (lower.contains(trigger)) {
                return true;
            }
        }
        return false;
    }

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

        // ─── SCRIPT-AWARE FILTER ─────────────────────────────────────
        // If ITS already has a scripted response for this message, skip Gemini.
        // This saves API calls and avoids double-responses.
        if (isHandledByITSScript(rawMessage)) {
            LOGGER.debug("[Chat] Message '{}' matches ITS script — skipping Gemini", rawMessage);
            return;
        }

        LOGGER.info("[Chat] Intercepted from {}: '{}' (no ITS script match → routing to Gemini)", playerName, rawMessage);

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
