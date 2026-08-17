package io.github.mizar107.zapegruntime;

import com.mojang.logging.LogUtils;
import io.github.mizar107.zapegruntime.network.SceneNetwork;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ZapeGRuntime.MOD_ID)
public final class ZapeGRuntime {

    public static final String MOD_ID = "zapeg_runtime";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ZapeGRuntime() {
        SceneNetwork.register();
    }
}
