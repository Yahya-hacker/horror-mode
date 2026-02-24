package net.mcreator.insidethesystem.meta;

import net.mcreator.insidethesystem.network.InsideTheSystemModVariables;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * EntityWatcher — Monitors entity lifecycle events from the original mod.
 *
 * Detects:
 *   1. AngryCoolPlayer303Entity spawning → triggers BREACH phase
 *   2. CoolPlayer303Entity dying → potential state transition
 *   3. MapVariables.Angry flag changes (checked via tick in MetaOrchestrator)
 *
 * This uses NeoForge events (no Mixins needed for entity monitoring).
 */
@EventBusSubscriber(modid = "sentient_coolplayer")
public class EntityWatcher {
    private static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer-EntityWatch");

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        String entityClass = event.getEntity().getClass().getSimpleName();

        // Detect when the Angry variant spawns — this is the BETRAYAL trigger
        if (entityClass.equals("AngryCoolPlayer303Entity")) {
            LOGGER.warn("[EntityWatch] ██ AngryCoolPlayer303 has entered the world! ██");

            MetaOrchestrator orchestrator = MetaOrchestrator.getInstance();
            if (orchestrator != null && orchestrator.getCurrentPhase() != MetaOrchestrator.Phase.BETRAYAL) {
                orchestrator.triggerPhaseChange(MetaOrchestrator.Phase.BETRAYAL);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        String entityClass = event.getEntity().getClass().getSimpleName();

        if (entityClass.equals("CoolPlayer303Entity")) {
            LOGGER.info("[EntityWatch] CoolPlayer303 has died. Checking state for escalation...");

            LevelAccessor world = event.getEntity().level();
            InsideTheSystemModVariables.MapVariables vars =
                    InsideTheSystemModVariables.MapVariables.get(world);

            if (vars != null && vars.Angry) {
                MetaOrchestrator orchestrator = MetaOrchestrator.getInstance();
                if (orchestrator != null && orchestrator.getCurrentPhase() == MetaOrchestrator.Phase.ALLY) {
                    orchestrator.triggerPhaseChange(MetaOrchestrator.Phase.BREACH);
                }
            }
        }
    }
}
