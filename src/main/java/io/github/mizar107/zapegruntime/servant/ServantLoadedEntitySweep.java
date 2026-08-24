package io.github.mizar107.zapegruntime.servant;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Bounded emergency sweep over entities already resident in loaded server levels. */
public final class ServantLoadedEntitySweep {

    private ServantLoadedEntitySweep() {}

    public static int discardAll(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        List<HeraldorServant> loaded = snapshot(server);
        int discarded = 0;
        for (HeraldorServant servant : loaded) {
            if (!servant.isRemoved()) {
                servant.discard();
                discarded++;
            }
        }
        return discarded;
    }

    public static int activeCount(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return snapshot(server).size();
    }

    private static List<HeraldorServant> snapshot(MinecraftServer server) {
        List<HeraldorServant> loaded = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof HeraldorServant servant && !servant.isRemoved()) {
                    loaded.add(servant);
                }
            }
        }
        return loaded;
    }
}
