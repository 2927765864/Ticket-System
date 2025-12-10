package common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Train implements Serializable {
    private static final long serialVersionUID = 1L;

    private String trainId;
    private String startStation;
    private String endStation;

    // 核心库存结构：日期 -> (席位类型 -> 余票数量)
    // 例如: "2025-12-01" -> { "二等座": 100, "商务座": 10 }
    private Map<String, Map<String, Integer>> inventory = new HashMap<>();

    public Train(String trainId, String startStation, String endStation) {
        this.trainId = trainId;
        this.startStation = startStation;
        this.endStation = endStation;
    }

    // 增加/设置库存
    public void addTickets(String date, String type, int num) {
        inventory.putIfAbsent(date, new HashMap<>());
        Map<String, Integer> dailyMap = inventory.get(date);
        int current = dailyMap.getOrDefault(type, 0);
        dailyMap.put(type, current + num);
    }

    // 获取特定日期、特定席位的余票
    public int getTickets(String date, String type) {
        if (!inventory.containsKey(date)) return 0;
        return inventory.get(date).getOrDefault(type, 0);
    }

    // 扣减库存 (返回是否成功)
    public boolean reduceTickets(String date, String type, int num) {
        int current = getTickets(date, type);
        if (current >= num) {
            inventory.get(date).put(type, current - num);
            return true;
        }
        return false;
    }

    // 回滚/退票
    public void returnTickets(String date, String type, int num) {
        addTickets(date, type, num);
    }

    // Getters
    public String getTrainId() { return trainId; }
    public String getStartStation() { return startStation; }
    public String getEndStation() { return endStation; }

    // 生成该车次在指定日期的摘要信息（用于发给客户端）
    public String toString(String date) {
        if (!inventory.containsKey(date)) {
            return trainId + " (" + startStation + "-" + endStation + ") [该日期无票]";
        }
        Map<String, Integer> seats = inventory.get(date);
        return String.format("%s (%s-%s) %s", trainId, startStation, endStation, seats.toString());
    }
}