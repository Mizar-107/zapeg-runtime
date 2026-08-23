package io.github.mizar107.zapegruntime;

import com.mojang.logging.LogUtils;
import io.github.mizar107.zapegruntime.client.OsScareConfig;
import io.github.mizar107.zapegruntime.network.SceneNetwork;
import io.github.mizar107.zapegruntime.servant.ServantEntities;
import io.github.mizar107.zapegruntime.sound.HeraldorSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ZapeGRuntime.MOD_ID)
public final class ZapeGRuntime {

    public static final String MOD_ID = "zapeg_runtime";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ZapeGRuntime() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ServantEntities.register(modBus);
        HeraldorSounds.register(modBus);
        SceneNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, OsScareConfig.SPEC);
    }
}
