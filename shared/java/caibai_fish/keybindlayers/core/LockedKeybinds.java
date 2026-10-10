package caibai_fish.keybindlayers.core;

import java.util.Set;

/**
 * 不允许玩家修改的按键。
 *
 * <p>有些键位改掉没有意义、甚至有害：快捷栏 1-9 与数字键的对应关系被大量 mod 与玩家的
 * 肌肉记忆依赖；全屏则是跨游戏的通用快捷键。这些键**直接不出现在「控制」界面里**
 * ——不是置灰，是整行都不显示（按用户要求），所以列表构建时就把它们过滤掉，
 * 标签生成时也跳过它们（免得留下空标签）。</p>
 *
 * <p>按用户要求**直接写死**（不是配置项）。真要放开的话，把这里的名字去掉重新编译即可。</p>
 */
public final class LockedKeybinds {

    /** 被锁定的按键名，即 {@code KeyMapping#getName()} 的返回值。 */
    private static final Set<String> LOCKED = Set.of(
            "key.hotbar.1", "key.hotbar.2", "key.hotbar.3",
            "key.hotbar.4", "key.hotbar.5", "key.hotbar.6",
            "key.hotbar.7", "key.hotbar.8", "key.hotbar.9",
            "key.fullscreen");

    private LockedKeybinds() {
    }

    /** 该按键是否被锁定（不允许修改，也不在设置界面里显示）。 */
    public static boolean isLocked(String mappingName) {
        return mappingName != null && LOCKED.contains(mappingName);
    }
}
