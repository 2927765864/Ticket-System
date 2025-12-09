package client;

import common.Message;
import common.MessageType;
import common.Order;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class TestClient {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 8888;

        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 连接
            out.writeObject(new Message("Client-Test", MessageType.CONNECT, ""));
            in.readObject();

            // 1. 先查一下 K303 有多少票
            System.out.println(">>> [1] 初始查票...");
            out.writeObject(new Message("Client-Test", MessageType.QUERY_TICKETS, ""));
            Message msg1 = (Message) in.readObject();
            System.out.println(msg1.getMsgPayload());

            // 2. 锁票 (买 2 张)
            System.out.println("\n>>> [2] 尝试锁票 K303 (2张)...");
            out.writeObject(new Message("Client-Test", MessageType.LOCK_TICKET, "K303,2"));
            Message response = (Message) in.readObject();
            System.out.println("<<< " + response.getMsgPayload());

            // 3. 再次查票 (确认票少了)
            System.out.println("\n>>> [3] 锁票后立即查票...");
            out.writeObject(new Message("Client-Test", MessageType.QUERY_TICKETS, ""));
            Message msg2 = (Message) in.readObject();
            System.out.println(msg2.getMsgPayload());

            // 4. 模拟不支付，等待超时 (62秒)
            System.out.println("\n>>> [4] ⏳ 故意不支付，等待 62 秒测试超时回滚...");
            for (int i = 0; i < 62; i++) {
                Thread.sleep(1000);
                if (i % 10 == 0) System.out.print(i + "s... ");
            }
            System.out.println("时间到！");

            // 5. 最后查票 (见证奇迹的时刻：票应该回来了)
            System.out.println("\n>>> [5] 超时后查票 (票应该变回去了)...");
            out.writeObject(new Message("Client-Test", MessageType.QUERY_TICKETS, ""));
            Message msg3 = (Message) in.readObject();
            System.out.println(msg3.getMsgPayload());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}