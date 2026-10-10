package caibai_fish.keybindlayers.core;

import java.util.Objects;

/**
 * 一个「操作层级」。
 *
 * <p>操作层级描述的是「这些按键在什么场合下会同时生效」。同一层级内的两个绑定
 * 绑定到同一个键，才是真正的冲突；不同层级（例如「游戏内」与「界面」）的同一个键
 * 永远不会同时响应，因此不该在设置界面里被标红。</p>
 *
 * <p>本类刻意不引用任何 Minecraft 类，这样 1.20.1 与 1.21.1 两个工程可以共用同一份实现，
 * 其它 mod 也可以在没有 MC 类路径的场合（例如配置文件生成器）复用层级模型。</p>
 */
public final class KeyLayer implements Comparable<KeyLayer> {

    private final String id;
    private final String translationKey;
    private final int order;

    /**
     * @param id    层级标识，小写字母 + 下划线，必须全局唯一，例如 {@code gameplay}
     * @param order 深度：数值越小越「底层」（越通用）。仅用于排序与展示，不参与冲突判定
     */
    public KeyLayer(String id, int order) {
        this.id = Objects.requireNonNull(id, "id");
        this.order = order;
        this.translationKey = "keybindlayers.layer." + id;
    }

    public String id() {
        return this.id;
    }

    /** 层级的本地化键，语言文件缺失时界面会直接显示该键名。 */
    public String translationKey() {
        return this.translationKey;
    }

    public int order() {
        return this.order;
    }

    @Override
    public int compareTo(KeyLayer other) {
        int byOrder = Integer.compare(this.order, other.order);
        return byOrder != 0 ? byOrder : this.id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof KeyLayer other && this.id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return "KeyLayer[" + this.id + "]";
    }
}
