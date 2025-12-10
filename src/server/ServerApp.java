package server;

import common.Train;
import javafx.application.Application;
import javafx.application.Platform;
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
    private TableView<Train> trainTable;
    private ObservableList<Train> trainData = FXCollections.observableArrayList();
    private ListView<String> clientListView;
    private ObservableList<String> clientList = FXCollections.observableArrayList();

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("12306 票务系统监控中心");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        Label titleLabel = new Label("🚄 票务系统服务端 - 实时监控");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        root.setTop(titleLabel);

        // --- 中间表格 ---
        trainTable = new TableView<>();

        TableColumn<Train, String> idCol = new TableColumn<>("车次");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTrainId()));
        idCol.setPrefWidth(80); // 固定宽度

        TableColumn<Train, String> routeCol = new TableColumn<>("区间");
        routeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStartStation() + "-" + data.getValue().getEndStation()));
        routeCol.setPrefWidth(120);

        // [核心修改] 库存详情列
        TableColumn<Train, String> invCol = new TableColumn<>("库存详情 (自动换行)");
        invCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedInventory()));

        // 自定义单元格渲染，支持换行
        invCol.setCellFactory(tc -> new TableCell<Train, String>() {
            private final Text text = new Text();
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    text.setText(item);
                    // 设置文字包裹宽度，略小于列宽
                    text.wrappingWidthProperty().bind(tc.widthProperty().subtract(10));
                    setGraphic(text);
                }
            }
        });
        invCol.setPrefWidth(400); // 给宽一点

        trainTable.getColumns().addAll(idCol, routeCol, invCol);
        trainTable.setItems(trainData);

        // 这一行很重要：让表格行高自动适应内容
        trainTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        root.setCenter(trainTable);

        // --- 右侧列表 ---
        VBox rightBox = new VBox(5);
        rightBox.setPadding(new Insets(0,0,0,10));
        rightBox.setPrefWidth(150);
        rightBox.getChildren().add(new Label("在线终端:"));
        clientListView = new ListView<>(clientList);
        rightBox.getChildren().add(clientListView);
        root.setRight(rightBox);

        // --- 底部日志 ---
        VBox bottomBox = new VBox(5);
        bottomBox.getChildren().add(new Label("日志:"));
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        bottomBox.getChildren().add(logArea);
        root.setBottom(bottomBox);

        startServerThread();
        startRefreshTask();

        // 窗口设宽一点，高一点，方便看多行数据
        Scene scene = new Scene(root, 900, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // ... startServerThread, startRefreshTask, appendLog 保持不变 ...

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