package server;

import common.Message;
import common.MessageType;
import common.Order;
import common.Train;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Collection;

public class ClientHandler extends Thread {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private TicketServer.ClientListener clientListener; // 监听器引用
    private String currentClientId = null; // 记录当前连接的ID

    // 构造函数接收监听器
    public ClientHandler(Socket socket, TicketServer.ClientListener clientListener) {
        this.socket = socket;
        this.clientListener = clientListener;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Message msg = (Message) in.readObject();

                // 第一次收到消息时，记录下它的 ClientID
                if (currentClientId == null && msg.getClientNo() != null) {
                    currentClientId = msg.getClientNo();
                }

                // 1. 连接 (上线通知)
                if (msg.getMsgType() == MessageType.CONNECT) {
                    System.out.println(">>> 终端上线: " + currentClientId);

                    // [关键] 通知界面：有个家伙上线了
                    if (clientListener != null) {
                        clientListener.onClientConnected(currentClientId);
                    }

                    reply(MessageType.RESPONSE_SUCCESS, "欢迎连接票务系统！");
                }

                // 2. 查票
                else if (msg.getMsgType() == MessageType.QUERY_TICKETS) {
                    Collection<Train> trains = TicketManager.getInstance().getAllTrains();
                    StringBuilder sb = new StringBuilder();
                    for (Train t : trains) {
                        sb.append(t.toString()).append("\n");
                    }
                    reply(MessageType.RESPONSE_SUCCESS, sb.toString());
                }

                // 3. 锁票
                else if (msg.getMsgType() == MessageType.LOCK_TICKET) {
                    String payload = msg.getMsgPayload();
                    String[] parts = payload.split(",");
                    if (parts.length == 2) {
                        String trainId = parts[0];
                        int num = Integer.parseInt(parts[1]);
                        Order order = TicketManager.getInstance().lockTicket(trainId, num, msg.getClientNo());
                        if (order != null) {
                            reply(MessageType.RESPONSE_SUCCESS, "购票成功！订单信息: " + order.toString());
                        } else {
                            reply(MessageType.RESPONSE_FAIL, "购票失败：余票不足或车次不存在。");
                        }
                    } else {
                        reply(MessageType.RESPONSE_FAIL, "格式错误");
                    }
                }

                // 4. 支付
                else if (msg.getMsgType() == MessageType.PAY_ORDER) {
                    boolean success = TicketManager.getInstance().payTicket(msg.getMsgPayload());
                    reply(success ? MessageType.RESPONSE_SUCCESS : MessageType.RESPONSE_FAIL,
                            success ? "支付成功" : "支付失败");
                }

                // 5. 取消订单 (保留上一步的功能)
                else if (msg.getMsgType() == MessageType.CANCEL_ORDER) {
                    boolean success = TicketManager.getInstance().cancelOrder(msg.getMsgPayload());
                    reply(success ? MessageType.RESPONSE_SUCCESS : MessageType.RESPONSE_FAIL,
                            success ? "订单已取消" : "取消失败");
                }

                // 6. 加车
                else if (msg.getMsgType() == MessageType.ADD_TRAIN) {
                    String[] parts = msg.getMsgPayload().split(",");
                    if (parts.length == 4) {
                        Train newTrain = new Train(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]));
                        TicketManager.getInstance().addTrain(newTrain);
                        reply(MessageType.RESPONSE_SUCCESS, "操作成功");
                    }
                }

                // 7. 断开
                else if (msg.getMsgType() == MessageType.DISCONNECT) {
                    break;
                }
            }
        } catch (Exception e) {
            // 客户端异常断开
        } finally {
            // [关键] 资源清理与下线通知
            if (currentClientId != null && clientListener != null) {
                clientListener.onClientDisconnected(currentClientId);
                System.out.println(">>> 终端下线: " + currentClientId);
            }
            try { if (socket != null) socket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void reply(MessageType type, String content) throws IOException {
        out.writeObject(new Message("Server", type, content));
        out.flush();
    }
}