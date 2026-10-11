// 本地编译兜底 stub：与 app/libs/libbox.aar 二选一（build.gradle 自动判断）。
// 仅保证 app 模块可编译通过，不含任何实现；发布构建必须使用 CI 产出的真实 aar。
package io.nekohasekai.libbox;

public class TunOptions {
    public int getMtu() { return 0; }
    public String getInet4Address() { return null; }
    public int getInet4PrefixLength() { return 0; }
    public String getInet6Address() { return null; }
    public int getInet6PrefixLength() { return 0; }
    public StringIterator getDnsServerAddress() { return null; }
    public StringIterator getRoutes() { return null; }
    public boolean getAutoRoute() { return false; }
    public boolean getStrictRoute() { return false; }
    public int getTableIndex() { return 0; }
}
