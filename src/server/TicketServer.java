package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 票务系统网络服务
 * 升级点：增加了 ClientListener 接口，用于通知界面更新在线列表
 */
public class TicketServer implements Runnable {
    private static final int PORT = 8888;
    private boolean isRunning = true;

    private LogCallback logCallback;
    private ClientListener clientListener; // 新增：客户端状态监听器

    // 构造函数：现在需要传入两个回调（日志回调 + 状态监听器）
    public TicketServer(LogCallback logCallback, ClientListener clientListener) {
        this.logCallback = logCallback;
        this.clientListener = clientListener;
    }

    @Override
    public void run() {
        log(">>> 票务系统服务端启动...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log("正在监听端口 " + PORT + "，等待连接...");

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                log("检测到物理连接: " + clientSocket.getInetAddress());

                // 将监听器传递给 Handler，让它去汇报具体是谁上线了
                ClientHandler handler = new ClientHandler(clientSocket, clientListener);
                handler.start();
            }
        } catch (IOException e) {
            log("服务端异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void log(String msg) {
        System.out.println(msg);
        if (logCallback != null) {
            logCallback.onLog(msg);
        }
    }

    // 日志回调接口
    public interface LogCallback {
        void onLog(String message);
    }

    // [新增] 客户端状态监听接口
    public interface ClientListener {
        void onClientConnected(String clientId);     // 谁上线了
        void onClientDisconnected(String clientId);  // 谁下线了
    }
}