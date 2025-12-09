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

public class ServerApp extends Application {

    private TextArea logArea;
    private TableView<Train> trainTable;
    private ObservableList<Train> trainData = FXCollections.observableArrayList();

    // [新增] 在线客户端列表数据源
    private ListView<String> clientListView;
    private ObservableList<String> clientList = FXCollections.observableArrayList();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("12306 票务系统监控中心");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        Label titleLabel = new Label("🚄 票务系统服务端 - 实时监控");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        root.setTop(titleLabel);

        // --- 中间：车次表格 ---
        trainTable = new TableView<>();

        TableColumn<Train, String> idCol = new TableColumn<>("车次号");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTrainId()));

        TableColumn<Train, String> startCol = new TableColumn<>("始发站");
        startCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStartStation()));

        TableColumn<Train, String> endCol = new TableColumn<>("终到站");
        endCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEndStation()));

        // [新增] 席位类型列 (应付课件要求，硬编码显示)
        TableColumn<Train, String> typeCol = new TableColumn<>("席位类型");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty("二等座"));

        TableColumn<Train, Integer> seatCol = new TableColumn<>("当前余票");
        seatCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getAvailableSeats()).asObject());
        seatCol.setCellFactory(column -> new TableCell<Train, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    if (item < 10) setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    else setStyle("-fx-text-fill: green;");
                }
            }
        });

        trainTable.getColumns().addAll(idCol, startCol, endCol, typeCol, seatCol);
        trainTable.setItems(trainData);
        trainTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        root.setCenter(trainTable);

        // --- [新增] 右侧：在线终端监控区域 ---
        VBox rightBox = new VBox(5);
        rightBox.setPadding(new Insets(0, 0, 0, 10));
        rightBox.setPrefWidth(150);
        rightBox.getChildren().add(new Label("在线终端列表:"));

        clientListView = new ListView<>(clientList); // 绑定数据源
        rightBox.getChildren().add(clientListView);

        root.setRight(rightBox);

        // --- 底部：日志 ---
        VBox bottomBox = new VBox(5);
        bottomBox.setPadding(new Insets(10, 0, 0, 0));
        bottomBox.getChildren().add(new Label("系统日志:"));
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        bottomBox.getChildren().add(logArea);
        root.setBottom(bottomBox);

        // 启动服务
        startServerThread();
        // 启动刷新
        startRefreshTask();

        Scene scene = new Scene(root, 750, 500); // 稍微宽一点，容纳右侧列表
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void startServerThread() {
        // 创建 TicketServer，传入两个回调：
        // 1. 日志回调 -> 写到底部 LogArea
        // 2. 客户端状态监听器 -> 更新右侧 clientList
        TicketServer server = new TicketServer(
                msg -> Platform.runLater(() -> appendLog(msg)),
                new TicketServer.ClientListener() {
                    @Override
                    public void onClientConnected(String clientId) {
                        Platform.runLater(() -> {
                            if (!clientList.contains(clientId)) {
                                clientList.add(clientId);
                            }
                        });
                    }

                    @Override
                    public void onClientDisconnected(String clientId) {
                        Platform.runLater(() -> {
                            clientList.remove(clientId);
                        });
                    }
                }
        );
        new Thread(server).start();
    }

    private void startRefreshTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> {
                trainData.clear();
                trainData.addAll(TicketManager.getInstance().getAllTrains());
                trainTable.refresh();
            });
        }, 0, 3, TimeUnit.SECONDS);
    }

    private void appendLog(String msg) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        logArea.appendText("[" + sdf.format(new Date()) + "] " + msg + "\n");
    }
}