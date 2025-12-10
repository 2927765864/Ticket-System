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
    private TicketServer.ClientListener clientListener;
    private String currentClientId = null;

    public ClientHandler(Socket socket, TicketServer.ClientListener clientListener) {
        this.socket = socket;
        this.clientListener = clientListener;
    }

    // [新增] 公共发送方法，供 SessionManager 调用进行推送
    public void sendMessage(Message msg) {
        try {
            // 加锁防止并发写入冲突
            if (out != null) {
                synchronized (out) {
                    out.writeObject(msg);
                    out.flush();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Message msg = (Message) in.readObject();
                if (currentClientId == null && msg.getClientNo() != null) {
                    currentClientId = msg.getClientNo();
                }

                // 1. 连接
                if (msg.getMsgType() == MessageType.CONNECT) {
                    System.out.println(">>> 终端上线: " + currentClientId);

                    // [关键] 注册到会话管理器，以便能收到推送
                    SessionManager.register(currentClientId, this);

                    if (clientListener != null) clientListener.onClientConnected(currentClientId);
                    reply(MessageType.RESPONSE_SUCCESS, "欢迎连接票务系统！");
                }

                // 2. 查票 (支持按日期查询)
                else if (msg.getMsgType() == MessageType.QUERY_TICKETS) {
                    // payload 是查询日期，如 "2025-12-01"
                    String queryDate = msg.getMsgPayload();
                    if (queryDate == null || queryDate.trim().isEmpty()) {
                        queryDate = java.time.LocalDate.now().toString(); // 默认查今天
                    }

                    Collection<Train> trains = TicketManager.getInstance().getAllTrains();
                    StringBuilder sb = new StringBuilder();
                    for (Train t : trains) {
                        // 调用 Train 新增的带日期 toString 方法
                        sb.append(t.toString(queryDate)).append("\n");
                    }
                    reply(MessageType.RESPONSE_SUCCESS, sb.toString());
                }

                // 3. 锁票 (参数升级: 车次,数量,日期,席位)
                else if (msg.getMsgType() == MessageType.LOCK_TICKET) {
                    String payload = msg.getMsgPayload();
                    // 格式: "K303,2,2025-12-01,商务座"
                    String[] parts = payload.split(",");
                    if (parts.length == 4) {
                        String trainId = parts[0];
                        int num = Integer.parseInt(parts[1]);
                        String date = parts[2];
                        String type = parts[3];

                        Order order = TicketManager.getInstance().lockTicket(trainId, num, msg.getClientNo(), date, type);
                        if (order != null) {
                            reply(MessageType.RESPONSE_SUCCESS, "购票成功！订单信息: " + order.toString());
                        } else {
                            reply(MessageType.RESPONSE_FAIL, "购票失败：余票不足或无此车次/席位");
                        }
                    } else {
                        reply(MessageType.RESPONSE_FAIL, "参数格式错误");
                    }
                }

                // 4. 支付
                else if (msg.getMsgType() == MessageType.PAY_ORDER) {
                    boolean success = TicketManager.getInstance().payTicket(msg.getMsgPayload());
                    reply(success ? MessageType.RESPONSE_SUCCESS : MessageType.RESPONSE_FAIL,
                            success ? "支付成功" : "支付失败");
                }

                // 5. 取消订单
                else if (msg.getMsgType() == MessageType.CANCEL_ORDER) {
                    boolean success = TicketManager.getInstance().cancelOrder(msg.getMsgPayload());
                    reply(success ? MessageType.RESPONSE_SUCCESS : MessageType.RESPONSE_FAIL,
                            success ? "订单已取消" : "取消失败");
                }

                // 6. 加车/放票 (参数升级: 车次,始发,终到,日期,席位,数量)
                else if (msg.getMsgType() == MessageType.ADD_TRAIN) {
                    String[] parts = msg.getMsgPayload().split(",");
                    if (parts.length == 6) {
                        String tid = parts[0];
                        String start = parts[1];
                        String end = parts[2];
                        String date = parts[3];
                        String type = parts[4];
                        int num = Integer.parseInt(parts[5]);

                        TicketManager.getInstance().addStock(tid, start, end, date, type, num);
                        reply(MessageType.RESPONSE_SUCCESS, "操作成功");
                    } else {
                        reply(MessageType.RESPONSE_FAIL, "格式错误");
                    }
                }

                // 7. 断开
                else if (msg.getMsgType() == MessageType.DISCONNECT) {
                    break;
                }
            }
        } catch (Exception e) {
            // ignore
        } finally {
            // [关键] 下线注销
            SessionManager.unregister(currentClientId);

            if (currentClientId != null && clientListener != null) {
                clientListener.onClientDisconnected(currentClientId);
                System.out.println(">>> 终端下线: " + currentClientId);
            }
            try { if (socket != null) socket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void reply(MessageType type, String content) throws IOException {
        synchronized (out) {
            out.writeObject(new Message("Server", type, content));
            out.flush();
        }
    }
}