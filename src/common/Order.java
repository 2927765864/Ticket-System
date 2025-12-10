package common;

import java.io.Serializable;
import java.util.Date;

public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status { PENDING, PAID, CANCELLED, TIMEOUT }

    private String orderId;
    private String clientNo;
    private String trainId;
    private int ticketCount;
    // [新增] 日期和席位
    private String travelDate;
    private String seatType;

    private Status status;
    private Date createTime;

    public Order(String orderId, String clientNo, String trainId, int ticketCount, String travelDate, String seatType) {
        this.orderId = orderId;
        this.clientNo = clientNo;
        this.trainId = trainId;
        this.ticketCount = ticketCount;
        this.travelDate = travelDate;
        this.seatType = seatType;
        this.status = Status.PENDING;
        this.createTime = new Date();
    }

    // Getters
    public String getOrderId() { return orderId; }
    public String getClientNo() { return clientNo; }
    public String getTrainId() { return trainId; }
    public int getTicketCount() { return ticketCount; }
    public String getTravelDate() { return travelDate; } // New
    public String getSeatType() { return seatType; }     // New
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Date getCreateTime() { return createTime; }

    @Override
    public String toString() {
        return String.format("[订单%s] %s %s %s %d张 (%s)",
                orderId, travelDate, trainId, seatType, ticketCount, status);
    }
}