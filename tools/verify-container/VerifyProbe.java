package verify;

import java.util.List;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.slf4j.Logger;

/**
 * 一次性探针：在 1.21.8 上加载用 1.21.1 编译的 keybindlayers jar，
 * 自己走一遍「选项 -> 控制 -> 按键绑定」，把每一步的结果打进日志。
 *
 * <p>判据只有一条：点下「按键绑定」之后，当前界面的类名是不是
 * {@code caibai_fish.keybindlayers.neoforge.client.LayerKeyBindsScreen}。
 * 那说明 ControlsScreenMixin 在 1.21.8 上仍然注入成功。
 */
@Mod(value = "verifyprobe", dist = Dist.CLIENT)
@EventBusSubscriber(modid = "verifyprobe", value = Dist.CLIENT)
public class VerifyProbe {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MOD_SCREEN = "caibai_fish.keybindlayers.neoforge.client.LayerKeyBindsScreen";

    private static int ticks;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ticks++;

        if (ticks == 40) {
            LOG.info("PROBE keybindlayers loaded = {}", ModList.get().isLoaded("keybindlayers"));
        } else if (ticks == 60) {
            mc.setScreen(new ControlsScreen(mc.screen, mc.options));
            LOG.info("PROBE opened {}",
                    mc.screen == null ? "null" : mc.screen.getClass().getName());
        } else if (ticks == 70) {
            Button button = findButton(mc.screen, Component.translatable("controls.keybinds").getString());
            LOG.info("PROBE keybinds button found = {}", button != null);
            if (button != null) {
                button.onPress();
            }
        } else if (ticks == 80) {
            Screen screen = mc.screen;
            String name = screen == null ? "null" : screen.getClass().getName();
            LOG.info("PROBE screen after press = {}", name);
            LOG.info("PROBE interception = {}", MOD_SCREEN.equals(name) ? "OK" : "FAILED");
            LOG.info("PROBE screen is OptionsSubScreen = {}",
                    screen instanceof net.minecraft.client.gui.screens.options.OptionsSubScreen);
        } else if (ticks == 90) {
            Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(),
                    message -> LOG.info("PROBE screenshot: {}", message.getString()));
        } else if (ticks == 130) {
            LOG.info("PROBE done, exiting");
            mc.stop();
        }
    }

    private static Button findButton(Screen screen, String label) {
        return screen == null ? null : find(screen.children(), label);
    }

    private static Button find(List<? extends GuiEventListener> listeners, String label) {
        for (GuiEventListener listener : listeners) {
            if (listener instanceof Button button && button.getMessage().getString().equals(label)) {
                return button;
            }
            if (listener instanceof ContainerEventHandler container) {
                Button nested = find(container.children(), label);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }
}
