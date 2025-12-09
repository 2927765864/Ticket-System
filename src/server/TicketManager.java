package server;

import common.Order;
import common.Train;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 票务管家 (最终完整版 - 含退票功能)
 * 职责：管理车次数据、处理锁票、支付、取消订单、超时监控、以及票源供给
 */
public class TicketManager {
    private static TicketManager instance = new TicketManager();

    // 内存数据库
    private ConcurrentHashMap<String, Train> trainMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Order> orderMap = new ConcurrentHashMap<>();

    private TicketManager() {
        initData();
        startTimeoutMonitor(); // 启动超时监控线程
    }

    public static TicketManager getInstance() {
        return instance;
    }

    private void initData() {
        // 初始测试数据
        Train t1 = new Train("G101", "北京", "上海", 200);
        Train t2 = new Train("D202", "北京", "天津", 100);
        Train t3 = new Train("K303", "西安", "成都", 5);
        trainMap.put(t1.getTrainId(), t1);
        trainMap.put(t2.getTrainId(), t2);
        trainMap.put(t3.getTrainId(), t3);
        System.out.println(">>> 票务数据初始化完成。");
    }

    public Collection<Train> getAllTrains() {
        return trainMap.values();
    }

    /**
     * 1. 锁票 (下单) - 互斥操作
     */
    public synchronized Order lockTicket(String trainId, int num, String clientNo) {
        if (num < 1 || num > 5) {
            System.out.println("❌ 锁票失败：非法购票数量 " + num + " (限制1~5人)");
            return null;
        }

        Train train = trainMap.get(trainId);
        if (train == null) return null;

        if (train.getAvailableSeats() >= num) {
            train.setAvailableSeats(train.getAvailableSeats() - num);

            String orderId = UUID.randomUUID().toString().substring(0, 8);
            Order newOrder = new Order(orderId, clientNo, trainId, num);
            orderMap.put(orderId, newOrder);

            System.out.println("✅ 锁票成功！[订单:" + orderId + "] " + trainId + " 剩余:" + train.getAvailableSeats());
            return newOrder;
        }
        return null;
    }

    /**
     * 2. 支付订单
     */
    public synchronized boolean payTicket(String orderId) {
        Order order = orderMap.get(orderId);
        if (order != null && order.getStatus() == Order.Status.PENDING) {
            order.setStatus(Order.Status.PAID);
            System.out.println("💰 支付成功！[订单:" + orderId + "]");
            return true;
        }
        return false;
    }

    /**
     * 3. 取消订单 (退票/撤单) - [新增功能]
     * 对应课件状态机 T4: 待支付 -> 已取消，并释放资源
     */
    public synchronized boolean cancelOrder(String orderId) {
        Order order = orderMap.get(orderId);

        // 只有“待支付”状态的订单可以被取消
        if (order != null && order.getStatus() == Order.Status.PENDING) {
            // 1. 修改状态
            order.setStatus(Order.Status.CANCELLED);

            // 2. 释放资源 (回滚余票)
            Train train = trainMap.get(order.getTrainId());
            if (train != null) {
                train.setAvailableSeats(train.getAvailableSeats() + order.getTicketCount());
            }

            System.out.println("🗑️ 订单已取消！[订单:" + orderId + "] 票已释放，余票恢复。");
            return true;
        }
        return false;
    }

    /**
     * 4. 动态增加车次/放票
     */
    public synchronized void addTrain(Train newTrain) {
        if (trainMap.containsKey(newTrain.getTrainId())) {
            Train oldTrain = trainMap.get(newTrain.getTrainId());
            int newSeats = oldTrain.getAvailableSeats() + newTrain.getAvailableSeats();
            oldTrain.setAvailableSeats(newSeats);
            System.out.println("➕ 车次 [" + newTrain.getTrainId() + "] 余票增加 " + newTrain.getAvailableSeats() + " 张");
        } else {
            trainMap.put(newTrain.getTrainId(), newTrain);
            System.out.println("🆕 新增车次 [" + newTrain.getTrainId() + "]");
        }
    }

    /**
     * 5. 启动后台监控线程
     */
    private void startTimeoutMonitor() {
        Thread monitorThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    long now = System.currentTimeMillis();
                    for (Order order : orderMap.values()) {
                        if (order.getStatus() == Order.Status.PENDING) {
                            if (now - order.getCreateTime().getTime() > 60 * 1000) {
                                handleTimeout(order);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
        System.out.println(">>> 🕒 订单超时监控线程已启动...");
    }

    private synchronized void handleTimeout(Order order) {
        if (order.getStatus() != Order.Status.PENDING) return;
        order.setStatus(Order.Status.TIMEOUT);
        Train train = trainMap.get(order.getTrainId());
        if (train != null) {
            train.setAvailableSeats(train.getAvailableSeats() + order.getTicketCount());
        }
        System.out.println("⏰ 订单超时失效！[订单:" + order.getOrderId() + "] 票已回滚。");
    }
}