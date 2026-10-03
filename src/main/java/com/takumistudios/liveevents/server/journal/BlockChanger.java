package com.takumistudios.liveevents.server.journal;

import com.takumistudios.liveevents.api.LiveEventCallbacks;
import com.takumistudios.liveevents.config.ServerConfig;
import com.takumistudios.liveevents.content.LEContent;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.instance.EventInstance;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Único punto por el que un evento cambia bloques (§8.4). Comprueba, por este orden: chunk cargado (nunca fuerza la
 * carga), bloques inmunes ({@code #liveevents:immune}), block entities (cofres, hornos…), zonas excluidas, la API
 * {@link LiveEventCallbacks#BEFORE_BLOCK_CHANGE}; después cambia el bloque en el hilo del servidor y lo apunta en el
 * diario.
 */
public final class BlockChanger {
    private BlockChanger() {
    }

    public static boolean set(EventInstance instance, ServerLevel level, BlockPos pos, BlockState to) {
        if (!level.isLoaded(pos) || pos.getY() < level.getMinY() || pos.getY() > level.getMaxY()) {
            return false;
        }
        BlockState from = level.getBlockState(pos);
        if (from == to || !canChange(level, pos, from)) {
            return false;
        }
        if (!LiveEventCallbacks.BEFORE_BLOCK_CHANGE.invoker().allowBlockChange(instance, level, pos, from, to)) {
            return false;
        }
        if (!level.setBlock(pos, to, Block.UPDATE_ALL, 512)) {
            return false;
        }
        WorldJournal.Journal journal = instance.journal();
        if (journal != null) {
            journal.record(level.dimension().identifier().toString(), pos.asLong(),
                    BlockStateParser.serialize(from), BlockStateParser.serialize(to));
        }
        return true;
    }

    /** ¿Puede un evento tocar este bloque? (sin contar la API) */
    public static boolean canChange(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.hasBlockEntity() || state.is(LEContent.IMMUNE)) {
            return false;
        }
        ServerConfig config = LiveEventsServer.get().config();
        if (config.respect_protections) {
            String dim = level.dimension().identifier().toString();
            for (ServerConfig.Region r : config.excluded_regions) {
                if (r.contains(dim, pos.getX(), pos.getY(), pos.getZ())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** ¿Está la posición en una zona excluida? (para el daño en zonas protegidas) */
    public static boolean inExcludedRegion(ServerLevel level, BlockPos pos) {
        ServerConfig config = LiveEventsServer.get().config();
        String dim = level.dimension().identifier().toString();
        for (ServerConfig.Region r : config.excluded_regions) {
            if (r.contains(dim, pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }
        return false;
    }
}
