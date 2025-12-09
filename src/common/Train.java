package common;

import java.io.Serializable;

/**
 * 列车实体类
 * 必须实现 Serializable 接口以便网络传输
 */
public class Train implements Serializable {
    private static final long serialVersionUID = 1L;

    private String trainId;     // 车次号
    private String startStation;// 始发站
    private String endStation;  // 终到站
    private int totalSeats;     // 总席位
    private int availableSeats; // 当前余票

    public Train(String trainId, String startStation, String endStation, int totalSeats) {
        this.trainId = trainId;
        this.startStation = startStation;
        this.endStation = endStation;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats; // 初始余票等于总票数
    }

    // --- Getter 和 Setter 方法 (补全了) ---

    public String getTrainId() {
        return trainId;
    }

    public void setTrainId(String trainId) {
        this.trainId = trainId;
    }

    public String getStartStation() {
        return startStation;
    }

    public void setStartStation(String startStation) {
        this.startStation = startStation;
    }

    public String getEndStation() {
        return endStation;
    }

    public void setEndStation(String endStation) {
        this.endStation = endStation;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
    }

    @Override
    public String toString() {
        return trainId + " (" + startStation + "-" + endStation + ") 余票:" + availableSeats;
    }
}