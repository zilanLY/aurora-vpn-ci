// 本地编译兜底 stub：与 app/libs/libbox.aar 二选一（build.gradle 自动判断）。
// 仅保证 app 模块可编译通过，不含任何实现；发布构建必须使用 CI 产出的真实 aar。
package io.nekohasekai.libbox;

public interface PlatformInterface {
    int openTun(TunOptions options);
    boolean usePlatformAutoDetectInterfaceControl();
    void autoDetectInterfaceControl(int fd);
    boolean useProcFS();
    void startDefaultInterfaceMonitor(InterfaceUpdateListener listener);
    void closeDefaultInterfaceMonitor(InterfaceUpdateListener listener);
    NetworkInterfaceIterator getInterfaces();
    boolean underNetworkExtension();
    boolean includeAllNetworks();
    void clearDNSCache();
    WIFIState readWIFIState();
    LocalDNSTransport localDNSTransport();
    boolean usePlatformShell();
    void checkPlatformShell();
    boolean usePlatformBridge();
    void registerMyInterface(String name);
    void startNeighborMonitor(NeighborUpdateListener listener);
    void closeNeighborMonitor(NeighborUpdateListener listener);
    ConnectionOwner findConnectionOwner(int ipProtocol, String sourceAddress, int sourcePort, String destinationAddress, int destinationPort);
    ShellSession openShellSession(PlatformUser user, String command, StringIterator environ, String term, int rows, int cols);
    PlatformUser lookupUser(String username);
    String lookupSFTPServer();
    String readSystemSSHHostKey();
    String tailscaleHostname();
    BridgeSession createBridge(BridgeOptions options);
    void sendNotification(Notification notification);
    void cancelNotification(String identifier, int typeID);
}
