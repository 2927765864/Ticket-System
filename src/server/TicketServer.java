package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 票务系统网络服务
 * 改造后：支持在一个独立线程中启动，避免卡死UI
 */
public class TicketServer implements Runnable {
    private static final int PORT = 8888;
    private boolean isRunning = true;

    // 一个简单的回调接口，用来把日志发给界面显示
    private LogCallback logCallback;

    public TicketServer(LogCallback callback) {
        this.logCallback = callback;
    }

    @Override
    public void run() {
        log(">>> 票务系统服务端启动...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log("正在监听端口 " + PORT + "，等待连接...");

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                log("检测到新连接: " + clientSocket.getInetAddress());

                // 启动接待员线程
                ClientHandler handler = new ClientHandler(clientSocket);
                handler.start();
            }
        } catch (IOException e) {
            log("服务端异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 辅助方法：打印日志到控制台 + 发送给界面
    private void log(String msg) {
        System.out.println(msg); // 控制台同时也打印
        if (logCallback != null) {
            logCallback.onLog(msg);
        }
    }

    // 定义回调接口
    public interface LogCallback {
        void onLog(String message);
    }
}