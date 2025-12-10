package server;

import common.Message;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器
 * 职责：记录在线客户端，提供点对点消息推送功能
 */
public class SessionManager {
    // 映射表：ClientNo -> ClientHandler
    private static ConcurrentHashMap<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();

    // 登记上线
    public static void register(String clientNo, ClientHandler handler) {
        if (clientNo != null && handler != null) {
            onlineClients.put(clientNo, handler);
        }
    }

    // 注销下线
    public static void unregister(String clientNo) {
        if (clientNo != null) {
            onlineClients.remove(clientNo);
        }
    }

    // 给指定用户发消息 (推送)
    public static void notifyClient(String clientNo, Message msg) {
        ClientHandler handler = onlineClients.get(clientNo);
        if (handler != null) {
            handler.sendMessage(msg);
        }
    }
}