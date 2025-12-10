package common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap; // 使用 TreeMap 让日期自动排序

public class Train implements Serializable {
    private static final long serialVersionUID = 1L;

    private String trainId;
    private String startStation;
    private String endStation;

    // 改用 TreeMap，这样显示时日期会自动按顺序排列，不会乱跳
    private Map<String, Map<String, Integer>> inventory = new TreeMap<>();

    public Train(String trainId, String startStation, String endStation) {
        this.trainId = trainId;
        this.startStation = startStation;
        this.endStation = endStation;
    }

    // 增加库存
    public void addTickets(String date, String type, int num) {
        inventory.putIfAbsent(date, new HashMap<>());
        Map<String, Integer> dailyMap = inventory.get(date);
        int current = dailyMap.getOrDefault(type, 0);
        dailyMap.put(type, current + num);
    }

    // 获取余票
    public int getTickets(String date, String type) {
        if (!inventory.containsKey(date)) return 0;
        return inventory.get(date).getOrDefault(type, 0);
    }

    // 扣减库存
    public boolean reduceTickets(String date, String type, int num) {
        int current = getTickets(date, type);
        if (current >= num) {
            inventory.get(date).put(type, current - num);
            return true;
        }
        return false;
    }

    // 回滚
    public void returnTickets(String date, String type, int num) {
        addTickets(date, type, num);
    }

    // [核心修改] 格式化库存信息，供界面显示
    public String getFormattedInventory() {
        if (inventory.isEmpty()) return "暂无排期";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, Integer>> entry : inventory.entrySet()) {
            String date = entry.getKey();
            sb.append("📅 ").append(date).append(": "); // 加个图标好看点

            Map<String, Integer> seats = entry.getValue();
            for (Map.Entry<String, Integer> seat : seats.entrySet()) {
                // 格式: 二等座(100)
                sb.append(seat.getKey()).append("(").append(seat.getValue()).append(")  ");
            }
            sb.append("\n"); // 换行
        }
        return sb.toString();
    }

    // Getters
    public String getTrainId() { return trainId; }
    public String getStartStation() { return startStation; }
    public String getEndStation() { return endStation; }

    // toString (Client端解析用)
    public String toString(String date) {
        if (!inventory.containsKey(date)) {
            return trainId + " (" + startStation + "-" + endStation + ") [该日期无票]";
        }
        Map<String, Integer> seats = inventory.get(date);
        return String.format("%s (%s-%s) %s", trainId, startStation, endStation, seats.toString());
    }
}