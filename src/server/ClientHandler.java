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

/**
 * 客户端处理线程 (完整版)
 * 支持：连接、查票、锁票、支付、以及[加车]
 */
public class ClientHandler extends Thread {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Message msg = (Message) in.readObject();
                System.out.println(">>> 收到消息: " + msg.getMsgType() + " 来自 " + msg.getClientNo());

                // 1. 连接
                if (msg.getMsgType() == MessageType.CONNECT) {
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

                // 3. 锁票 (买票)
                else if (msg.getMsgType() == MessageType.LOCK_TICKET) {
                    String payload = msg.getMsgPayload(); // "K303,2"
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

                // 5. 加车 (新增功能 - 票源系统专用)
                else if (msg.getMsgType() == MessageType.ADD_TRAIN) {
                    // payload格式: "车次,始发,终到,票数"
                    String payload = msg.getMsgPayload();
                    String[] parts = payload.split(",");
                    if (parts.length == 4) {
                        String trainId = parts[0];
                        String start = parts[1];
                        String end = parts[2];
                        int seats = Integer.parseInt(parts[3]);

                        Train newTrain = new Train(trainId, start, end, seats);
                        TicketManager.getInstance().addTrain(newTrain);

                        reply(MessageType.RESPONSE_SUCCESS, "车次 " + trainId + " 操作成功！");
                    } else {
                        reply(MessageType.RESPONSE_FAIL, "格式错误，应为: 车次,始发,终到,票数");
                    }
                }

                // 6. 断开
                else if (msg.getMsgType() == MessageType.DISCONNECT) {
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("客户端断开: " + e.getMessage());
        } finally {
            try { if (socket != null) socket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void reply(MessageType type, String content) throws IOException {
        out.writeObject(new Message("Server", type, content));
        out.flush();
    }
}