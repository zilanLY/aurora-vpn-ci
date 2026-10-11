// 本地编译兜底 stub：与 app/libs/libbox.aar 二选一（build.gradle 自动判断）。
// 仅保证 app 模块可编译通过，不含任何实现；发布构建必须使用 CI 产出的真实 aar。
package io.nekohasekai.libbox;

public class SetupOptions {
    public String basePath;
    public String workingPath;
    public String tempPath;
    public boolean fixAndroidStack;
    public int logMaxLines;
    public boolean debug;
    public String crashReportSource;
    public String appVersion;
}
