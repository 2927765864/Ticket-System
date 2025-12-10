package server;

import common.Message;
import common.MessageType;
import common.Order;
import common.Train;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TicketManager {
    private static TicketManager instance = new TicketManager();
    private ConcurrentHashMap<String, Train> trainMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Order> orderMap = new ConcurrentHashMap<>();

    private TicketManager() {
        initData(); // 初始化少量测试数据
        startTimeoutMonitor();
    }

    public static TicketManager getInstance() { return instance; }

    private void initData() {
        // 预设一些数据方便测试
        Train t1 = new Train("G101", "北京", "上海");
        // 给 G101 加今天的票
        String today = java.time.LocalDate.now().toString();
        t1.addTickets(today, "二等座", 100);
        t1.addTickets(today, "一等座", 50);
        t1.addTickets(today, "商务座", 10);

        trainMap.put(t1.getTrainId(), t1);
        System.out.println(">>> 票务数据初始化完成 (日期版)。");
    }

    public Collection<Train> getAllTrains() { return trainMap.values(); }

    // [修改] 锁票：增加 date 和 seatType 参数
    public synchronized Order lockTicket(String trainId, int num, String clientNo, String date, String seatType) {
        Train train = trainMap.get(trainId);
        if (train == null) return null;

        // 核心：扣减特定库存
        if (train.reduceTickets(date, seatType, num)) {
            String orderId = UUID.randomUUID().toString().substring(0, 8);
            Order newOrder = new Order(orderId, clientNo, trainId, num, date, seatType);
            orderMap.put(orderId, newOrder);

            System.out.println("✅ 锁票成功: " + newOrder);
            return newOrder;
        }
        return null;
    }

    // [修改] 增加库存：支持日期和席位
    public synchronized void addStock(String trainId, String start, String end, String date, String type, int num) {
        Train train = trainMap.computeIfAbsent(trainId, k -> new Train(trainId, start, end));
        train.addTickets(date, type, num);
        System.out.println("➕ 放票成功: [" + date + "] " + trainId + " " + type + "+" + num);
    }

    public synchronized boolean cancelOrder(String orderId) {
        Order order = orderMap.get(orderId);
        if (order != null && order.getStatus() == Order.Status.PENDING) {
            order.setStatus(Order.Status.CANCELLED);
            // 回滚库存
            Train train = trainMap.get(order.getTrainId());
            if (train != null) {
                train.returnTickets(order.getTravelDate(), order.getSeatType(), order.getTicketCount());
            }
            notifyOrderUpdate(order);
            return true;
        }
        return false;
    }

    // 超时处理同理
    private synchronized void handleTimeout(Order order) {
        if (order.getStatus() != Order.Status.PENDING) return;
        order.setStatus(Order.Status.TIMEOUT);
        Train train = trainMap.get(order.getTrainId());
        if (train != null) {
            train.returnTickets(order.getTravelDate(), order.getSeatType(), order.getTicketCount());
        }
        notifyOrderUpdate(order);
        System.out.println("⏰ 超时回滚: " + order.getOrderId());
    }

    public synchronized boolean payTicket(String orderId) {
        Order order = orderMap.get(orderId);
        if (order != null && order.getStatus() == Order.Status.PENDING) {
            order.setStatus(Order.Status.PAID);
            notifyOrderUpdate(order);
            return true;
        }
        return false;
    }

    private void notifyOrderUpdate(Order order) {
        String payload = order.getOrderId() + "," + order.getStatus().toString();
        Message msg = new Message("Server", MessageType.ORDER_UPDATE, payload);
        SessionManager.notifyClient(order.getClientNo(), msg);
    }

    // 启动监控线程(代码省略，同前)
    private void startTimeoutMonitor() {
        Thread t = new Thread(() -> {
            while(true) {
                try { Thread.sleep(1000);
                    long now = System.currentTimeMillis();
                    for(Order o : orderMap.values()) {
                        if(o.getStatus()==Order.Status.PENDING && now - o.getCreateTime().getTime() > 60000) {
                            handleTimeout(o);
                        }
                    }
                } catch(Exception e){}
            }
        });
        t.setDaemon(true); t.start();
    }
}