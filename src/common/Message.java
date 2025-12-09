package common;

import java.io.Serializable;

/**
 * 通信协议实体类
 * 必须实现 Serializable 接口，以便通过 Socket 的 ObjectOutputStream 传输
 * 对应课件第18页定义的 Message 类 [cite: 545]
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    // 终端序号/ID (对应课件 clientNo) [cite: 547]
    private String clientNo;

    // 消息类型 (对应课件 msgType) [cite: 548]
    // 建议存 int 或者直接存枚举，这里为了匹配课件说明，我们在getter/setter处理，内部存枚举更方便逻辑判断
    private MessageType msgType;

    // 消息内容 (对应课件 msgPayload) [cite: 548]
    // 这里存具体的数据，比如“车次信息”的JSON字符串，或者具体的对象
    private String msgPayload;

    // 构造函数
    public Message() {}

    public Message(String clientNo, MessageType msgType, String msgPayload) {
        this.clientNo = clientNo;
        this.msgType = msgType;
        this.msgPayload = msgPayload;
    }

    // Getter 和 Setter 方法
    public String getClientNo() { return clientNo; }
    public void setClientNo(String clientNo) { this.clientNo = clientNo; }

    public MessageType getMsgType() { return msgType; }
    public void setMsgType(MessageType msgType) { this.msgType = msgType; }

    public String getMsgPayload() { return msgPayload; }
    public void setMsgPayload(String msgPayload) { this.msgPayload = msgPayload; }

    @Override
    public String toString() {
        return "Message{client=" + clientNo + ", type=" + msgType + ", payload='" + msgPayload + "'}";
    }
}