package server;

import common.Order; // 引入 Order 类
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
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerApp extends Application {

    private TextArea logArea;
    private ListView<String> clientListView;
    private ObservableList<String> clientList = FXCollections.observableArrayList();

    // 车次表
    private TableView<Train> trainTable;
    private ObservableList<Train> trainData = FXCollections.observableArrayList();

    // [新增] 订单表
    private TableView<Order> orderTable;
    private ObservableList<Order> orderData = FXCollections.observableArrayList();

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("12306 票务系统监控中心");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        Label titleLabel = new Label("🚄 票务系统服务端 - 全局监控");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        root.setTop(titleLabel);

        // ===================================
        // 中间区域：上下分割 (车次 / 订单)
        // ===================================
        SplitPane centerSplit = new SplitPane();
        centerSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // 1. 上半部分：车次监控
        VBox trainBox = new VBox(5);
        trainBox.getChildren().add(new Label("车次库存监控:"));
        trainTable = new TableView<>();
        setupTrainTable();
        trainTable.setItems(trainData);
        trainBox.getChildren().add(trainTable);

        // 2. 下半部分：订单监控 (新增)
        VBox orderBox = new VBox(5);
        orderBox.getChildren().add(new Label("实时订单流水:"));
        orderTable = new TableView<>();
        setupOrderTable(); // 初始化订单列
        orderTable.setItems(orderData);
        orderBox.getChildren().add(orderTable);

        centerSplit.getItems().addAll(trainBox, orderBox);
        centerSplit.setDividerPositions(0.5); // 各占50%
        root.setCenter(centerSplit);

        // ===================================
        // 右侧：在线终端
        // ===================================
        VBox rightBox = new VBox(5);
        rightBox.setPadding(new Insets(0,0,0,10));
        rightBox.setPrefWidth(150);
        rightBox.getChildren().add(new Label("在线终端:"));
        clientListView = new ListView<>(clientList);
        rightBox.getChildren().add(clientListView);
        root.setRight(rightBox);

        // ===================================
        // 底部：系统日志
        // ===================================
        VBox bottomBox = new VBox(5);
        bottomBox.getChildren().add(new Label("系统日志:"));
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(100);
        bottomBox.getChildren().add(logArea);
        root.setBottom(bottomBox);

        // 启动后台任务
        startServerThread();
        startRefreshTask();

        Scene scene = new Scene(root, 1000, 700); // 窗口调大一点
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // 设置车次表格列
    private void setupTrainTable() {
        TableColumn<Train, String> idCol = new TableColumn<>("车次");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTrainId()));
        idCol.setPrefWidth(80);

        TableColumn<Train, String> routeCol = new TableColumn<>("区间");
        routeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStartStation() + "-" + data.getValue().getEndStation()));
        routeCol.setPrefWidth(120);

        TableColumn<Train, String> invCol = new TableColumn<>("库存详情 (自动换行)");
        invCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedInventory()));
        invCol.setCellFactory(tc -> new TableCell<Train, String>() {
            private final Text text = new Text();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); }
                else {
                    text.setText(item);
                    text.wrappingWidthProperty().bind(tc.widthProperty().subtract(10));
                    setGraphic(text);
                }
            }
        });
        invCol.setPrefWidth(400);

        trainTable.getColumns().addAll(idCol, routeCol, invCol);
        trainTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    // [新增] 设置订单表格列
    private void setupOrderTable() {
        TableColumn<Order, String> idCol = new TableColumn<>("订单号");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOrderId()));

        TableColumn<Order, String> clientCol = new TableColumn<>("终端ID");
        clientCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getClientNo()));

        TableColumn<Order, String> trainCol = new TableColumn<>("车次");
        trainCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTrainId()));

        TableColumn<Order, String> infoCol = new TableColumn<>("购票详情");
        infoCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getTravelDate() + " / " + data.getValue().getSeatType() + " / " + data.getValue().getTicketCount() + "张"
        ));
        infoCol.setPrefWidth(200);

        TableColumn<Order, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus().toString()));
        statusCol.setCellFactory(column -> new TableCell<Order, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else {
                    setText(item);
                    if ("PENDING".equals(item)) setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                    else if ("PAID".equals(item)) setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    else setStyle("-fx-text-fill: gray;"); // TIMEOUT, CANCELLED
                }
            }
        });

        orderTable.getColumns().addAll(idCol, clientCol, trainCol, infoCol, statusCol);
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    // 启动服务器线程
    private void startServerThread() {
        TicketServer server = new TicketServer(
                msg -> Platform.runLater(() -> appendLog(msg)),
                new TicketServer.ClientListener() {
                    @Override public void onClientConnected(String id) { Platform.runLater(() -> {if(!clientList.contains(id)) clientList.add(id);}); }
                    @Override public void onClientDisconnected(String id) { Platform.runLater(() -> clientList.remove(id)); }
                }
        );
        new Thread(server).start();
    }

    // [修改] 刷新任务：同时刷新车次和订单
    private void startRefreshTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> {
                // 1. 刷新车次
                trainData.clear();
                trainData.addAll(TicketManager.getInstance().getAllTrains());
                trainTable.refresh();

                // 2. 刷新订单 (新增)
                // 注意：这里为了简单直接全量刷新。在数据量巨大时应该做增量更新，但大作业足够了。
                orderData.clear();
                orderData.addAll(TicketManager.getInstance().getAllOrders());
                orderTable.refresh();
            });
        }, 0, 3, TimeUnit.SECONDS); // 每3秒刷新一次
    }

    private void appendLog(String msg) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        logArea.appendText("[" + sdf.format(new Date()) + "] " + msg + "\n");
    }
}