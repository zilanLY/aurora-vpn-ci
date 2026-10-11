// 本地编译兜底 stub：与 app/libs/libbox.aar 二选一（build.gradle 自动判断）。
// 仅保证 app 模块可编译通过，不含任何实现；发布构建必须使用 CI 产出的真实 aar。
// API 形状对齐 sing-box v1.14.2 gomobile 生成代码。
package io.nekohasekai.libbox;

public final class Libbox {
    public static final int InterfaceTypeWIFI = 1;
    public static final int InterfaceTypeCellular = 2;
    public static final int InterfaceTypeEthernet = 3;
    public static final int InterfaceTypeOther = 0;

    public static void setLocale(String tag) {}
    public static void setup(SetupOptions options) {}
    public static CommandServer newCommandServer(CommandServerHandler handler, Object ctx) { return null; }
    private Libbox() {}
}
