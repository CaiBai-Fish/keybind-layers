package caibai_fish.keybindlayers.neoforge.client;

import caibai_fish.keybindlayers.client.KeyContexts;
import caibai_fish.keybindlayers.neoforge.KeybindLayersNeoForge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * 采样「不开界面也能生效」的自定义作用域。
 *
 * <h2>为什么需要它</h2>
 * <p>不少 mod 的按键条件只是「装备/手持了某样东西」——例如 Sophisticated Backpacks 的
 * 「打开背包」。这类键在游戏进行中随时可用，会和移动、攻击同时响应，本该并进游戏内层；
 * 但它们用的是自定义 context，读不出标准值，一开始被当成了单独的界面层。</p>
 *
 * <p>好在这些 context 的 {@code isActive()} 会把「有没有打开界面」算进去。例如
 * {@code BackpackKeyConflictContext}：</p>
 *
 * <pre>{@code
 * public boolean isActive() {
 *     if (!KeyConflictContext.GUI.isActive()) return true;        // 没有界面 → 活跃
 *     return Minecraft.getInstance().screen instanceof AbstractContainerScreen;
 * }
 * }</pre>
 *
 * <p>所以在<strong>没有界面打开</strong>的时候调一次 {@code isActive()}：返回 true 的
 * 就是不要求界面、属于游戏内作用域的那些。JEI 那种「只在自家界面生效」的 context
 * 此时返回 false，自然被排除。</p>
 *
 * <h2>两个采样时机</h2>
 * <ul>
 *   <li>{@link #onTick}：进入世界之后、无界面时顺带采一次；</li>
 *   <li>{@link #sampleNow()}：打开按键界面时补一次。少了这个，玩家若一直待在主菜单
 *       （主菜单本身也是一个 Screen），这些作用域就永远并不进游戏内层。</li>
 * </ul>
 */
@EventBusSubscriber(modid = KeybindLayersNeoForge.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class InGameContextSampler {

    private InGameContextSampler() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // 只在「已进入世界、且没有任何界面打开」时采样。
        if (minecraft.screen != null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        sampleAll(minecraft);
    }

    /**
     * 立刻采样一次。
     *
     * <p>{@code isActive()} 的判定依赖「当前有没有界面」，而打开按键界面时恰恰是有界面的，
     * 所以这里临时把 {@code Minecraft.screen} 置空再采样、随即恢复——同步执行，
     * 不涉及渲染，也不会让别的代码观察到这个中间状态。</p>
     */
    public static void sampleNow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) {
            return;
        }
        if (minecraft.screen == null) {
            sampleAll(minecraft);
            return;
        }
        Screen saved = minecraft.screen;
        minecraft.screen = null;
        try {
            sampleAll(minecraft);
        } finally {
            minecraft.screen = saved;
        }
    }

    private static void sampleAll(Minecraft minecraft) {
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            IKeyConflictContext context = mapping.getKeyConflictContext();
            if (context == null || isStandard(context) || KeyContexts.isInGameContext(context)) {
                continue;
            }
            try {
                if (context.isActive()) {
                    KeyContexts.markInGameContext(context);
                }
            } catch (RuntimeException ignored) {
                // mod 自己的实现抛异常不该拖累我们
            }
        }
    }

    /** 三个标准 context 由注入的解析器直接处理，不需要采样。 */
    private static boolean isStandard(IKeyConflictContext context) {
        return context == KeyConflictContext.IN_GAME
                || context == KeyConflictContext.GUI
                || context == KeyConflictContext.UNIVERSAL;
    }
}
