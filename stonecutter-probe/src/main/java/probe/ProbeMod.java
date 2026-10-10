package probe;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 最小验证：同一份源码，靠 Stonecutter 的条件注释在两个 Minecraft 版本上编译出不同的类。
 */
@Mod("probe")
public class ProbeMod {

    private static final Logger LOGGER = LoggerFactory.getLogger("probe");

    public ProbeMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Probe: constructor reached");

        // 下面两个分支只会有一个进入编译——被排除的那个整段是注释。
        //? if >=1.21.4 {
        /*LOGGER.info("VERSION-BRANCH: compiled for 1.21.4+");
        *///?} else {
        LOGGER.info("VERSION-BRANCH: compiled for 1.21.1");
        //?}
    }
}
