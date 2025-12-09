package server;

import common.Train;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 票务系统监控大屏 (JavaFX版)
 * 对应课件要求：实时监控余票、订单状态，刷新周期3s
 */
public class ServerApp extends Application {

    private TextArea logArea; // 日志显示区
    private TableView<Train> trainTable; // 车次列表
    private ObservableList<Train> trainData = FXCollections.observableArrayList(); // 表格数据源

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("12306 票务系统监控中心");

        // 1. 布局容器
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // 2. 顶部标题
        Label titleLabel = new Label("🚄 票务系统服务端 - 实时监控");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        root.setTop(titleLabel);

        // 3. 中间表格：显示车次信息
        trainTable = new TableView<>();

        // 创建列
        TableColumn<Train, String> idCol = new TableColumn<>("车次号");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTrainId()));

        TableColumn<Train, String> startCol = new TableColumn<>("始发站");
        startCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStartStation()));

        TableColumn<Train, String> endCol = new TableColumn<>("终到站");
        endCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEndStation()));

        TableColumn<Train, Integer> seatCol = new TableColumn<>("当前余票");
        seatCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getAvailableSeats()).asObject());
        // 给余票列加个颜色，票少的时候显示红色
        seatCol.setCellFactory(column -> new TableCell<Train, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    if (item < 10) {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: green;");
                    }
                }
            }
        });

        trainTable.getColumns().addAll(idCol, startCol, endCol, seatCol);
        trainTable.setItems(trainData); // 绑定数据源
        trainTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); // 列宽自适应

        root.setCenter(trainTable);

        // 4. 底部日志区
        VBox bottomBox = new VBox(5);
        bottomBox.setPadding(new Insets(10, 0, 0, 0));
        bottomBox.getChildren().add(new Label("系统日志:"));

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(150);
        bottomBox.getChildren().add(logArea);

        root.setBottom(bottomBox);

        // 5. 启动 Server 后台线程
        startServerThread();

        // 6. 启动 UI 自动刷新任务 (3秒一次)
        startRefreshTask();

        Scene scene = new Scene(root, 600, 500);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * 启动 Socket 服务端线程
     */
    private void startServerThread() {
        // 创建 TicketServer 实例，并传入一个日志回调
        TicketServer server = new TicketServer(msg -> {
            // JavaFX 更新 UI 必须在主线程 (Platform.runLater)
            Platform.runLater(() -> appendLog(msg));
        });

        // 放到新线程里跑
        new Thread(server).start();
    }

    /**
     * 启动定时刷新任务 (课件要求：刷新周期 3s)
     */
    private void startRefreshTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            // 获取最新数据
            Platform.runLater(() -> {
                // 从 TicketManager 拿所有车次
                trainData.clear();
                trainData.addAll(TicketManager.getInstance().getAllTrains());
                trainTable.refresh();
                // appendLog("监控数据已刷新..."); // 如果觉得日志太吵，可以注释掉这行
            });
        }, 0, 3, TimeUnit.SECONDS); // 0秒延迟，3秒周期
    }

    // 辅助方法：追加日志并添加时间戳
    private void appendLog(String msg) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String time = sdf.format(new Date());
        logArea.appendText("[" + time + "] " + msg + "\n");
    }
}