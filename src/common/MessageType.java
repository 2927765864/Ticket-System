package common;

/**
 * 消息类型枚举
 * 对应课件中 msgType 的定义
 */
public enum MessageType {
    // 基础消息
    CONNECT(1),         // 客户端/票源系统连接/开机 [cite: 548]
    DISCONNECT(0),      // 断开连接

    // 业务消息 - 客户端发给服务端
    QUERY_TICKETS(2),   // 查询车次
    LOCK_TICKET(3),     // 锁定票源（下单）
    PAY_ORDER(4),       // 支付订单
    CANCEL_ORDER(5),    // 取消订单

    // 业务消息 - 服务端回发
    RESPONSE_SUCCESS(200), // 操作成功
    RESPONSE_FAIL(500),    // 操作失败（如无票、系统错误）
    DATA_UPDATE(6),        // 服务端推送的数据更新（如余票变化）

    // 票源系统消息
    ADD_TRAIN(7);       // 增加车次/放票

    private int value;

    MessageType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}