package common;

import java.io.Serializable;
import java.util.Date;

/**
 * 订单实体类
 * 对应课件中的订单概念，包含订单状态
 */
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    // 订单状态枚举 (对应课件第5页的状态机)
    public enum Status {
        PENDING,    // 待支付 (锁定状态)
        PAID,       // 已支付 (购票成功)
        CANCELLED,  // 已取消 (用户主动取消)
        TIMEOUT     // 已超时 (系统自动回收)
    }

    private String orderId;     // 订单号 (唯一标识)
    private String clientNo;    // 谁买的
    private String trainId;     // 买的哪趟车
    private int ticketCount;    // 买了几张
    private Status status;      // 当前状态
    private Date createTime;    // 创建时间 (用于计算是否超时)

    public Order(String orderId, String clientNo, String trainId, int ticketCount) {
        this.orderId = orderId;
        this.clientNo = clientNo;
        this.trainId = trainId;
        this.ticketCount = ticketCount;
        this.status = Status.PENDING; // 默认初始状态为“待支付”
        this.createTime = new Date();
    }

    // --- Getter 和 Setter ---
    public String getOrderId() { return orderId; }
    public String getClientNo() { return clientNo; }
    public String getTrainId() { return trainId; }
    public int getTicketCount() { return ticketCount; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Date getCreateTime() { return createTime; }

    @Override
    public String toString() {
        return String.format("[订单%s] %s 购买 %d张 %s (状态:%s)",
                orderId, clientNo, ticketCount, trainId, status);
    }
}