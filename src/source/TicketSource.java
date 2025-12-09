package source;

import common.Message;
import common.MessageType;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

/**
 * 票源系统客户端
 * 独立进程：负责向票务系统释放票源、增开车次 [cite: 13]
 */
public class TicketSource {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 8888;

        System.out.println(">>> 🚂 票源管理系统启动...");
        System.out.println(">>> 正在连接票务中心...");

        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 1. 握手连接
            out.writeObject(new Message("Source-Admin", MessageType.CONNECT, "Admin Login"));
            in.readObject(); // 接收欢迎消息

            Scanner scanner = new Scanner(System.in);
            System.out.println(">>> ✅ 连接成功！");

            while (true) {
                System.out.println("\n------------------------------------------------");
                System.out.println("请输入放票指令 (格式: 车次,始发,终到,票数)");
                System.out.println("例如: T999,北京,哈尔滨,200 (输入 exit 退出)");
                System.out.print("指令 > ");

                String input = scanner.nextLine();
                if ("exit".equalsIgnoreCase(input)) {
                    out.writeObject(new Message("Source-Admin", MessageType.DISCONNECT, ""));
                    break;
                }

                if (input.trim().isEmpty()) continue;

                // 2. 发送加车指令
                Message addMsg = new Message("Source-Admin", MessageType.ADD_TRAIN, input);
                out.writeObject(addMsg);
                out.flush();

                // 3. 等待结果
                Message response = (Message) in.readObject();
                System.out.println("<<< Server反馈: " + response.getMsgPayload());
            }

        } catch (Exception e) {
            System.out.println("❌ 连接异常: " + e.getMessage());
            System.out.println("请确认 TicketServer 已经启动。");
        }
    }
}