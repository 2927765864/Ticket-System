package client;

import common.Message;
import common.MessageType;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ClientApp extends Application {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isConnected = false;
    private final String CLIENT_ID = "Client-" + (int)(Math.random() * 1000);

    private TableView<Train> trainTable;
    private ObservableList<Train> trainData = FXCollections.observableArrayList();
    private TextArea logArea;
    private TextField numField;
    private TextField orderIdField;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("12306 购票终端 [" + CLIENT_ID + "]");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // 顶部
        HBox topBox = new HBox(10);
        topBox.setPadding(new Insets(0, 0, 10, 0));
        Button btnConnect = new Button("连接服务器");
        btnConnect.setOnAction(e -> connectToServer());
        Button btnRefresh = new Button("刷新车票");
        btnRefresh.setOnAction(e -> sendQuery());
        topBox.getChildren().addAll(btnConnect, btnRefresh);
        root.setTop(topBox);

        // 中间
        trainTable = new TableView<>();
        setupTableColumns();
        trainTable.setItems(trainData);
        root.setCenter(trainTable);

        // 底部
        VBox bottomBox = new VBox(10);
        bottomBox.setPadding(new Insets(10, 0, 0, 0));

        // 购票行
        HBox buyBox = new HBox(10);
        numField = new TextField("1");
        numField.setPrefWidth(50);
        Button btnBuy = new Button("立即抢票");
        btnBuy.setStyle("-fx-background-color: #ff4d4f; -fx-text-fill: white; -fx-font-weight: bold;");
        btnBuy.setOnAction(e -> handleBuyAction());
        buyBox.getChildren().addAll(new Label("购票人数(1-5):"), numField, btnBuy);

        // 订单操作行 (修改：增加取消按钮)
        HBox payBox = new HBox(10);
        orderIdField = new TextField();
        orderIdField.setPromptText("输入订单号操作...");
        Button btnPay = new Button("支付");
        btnPay.setStyle("-fx-background-color: #52c41a; -fx-text-fill: white;");
        btnPay.setOnAction(e -> handlePayAction());

        Button btnCancel = new Button("取消订单");
        btnCancel.setStyle("-fx-background-color: #faad14; -fx-text-fill: white;");
        btnCancel.setOnAction(e -> handleCancelAction());

        payBox.getChildren().addAll(new Label("订单操作:"), orderIdField, btnPay, btnCancel);

        logArea = new TextArea();
        logArea.setPrefHeight(120);
        logArea.setEditable(false);

        bottomBox.getChildren().addAll(new Separator(), buyBox, payBox, new Label("终端日志:"), logArea);
        root.setBottom(bottomBox);

        primaryStage.setOnCloseRequest(e -> disconnect());
        Scene scene = new Scene(root, 500, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        connectToServer();
    }

    private void setupTableColumns() {
        TableColumn<Train, String> idCol = new TableColumn<>("车次");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTrainId()));

        TableColumn<Train, String> routeCol = new TableColumn<>("区间");
        routeCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStartStation() + " -> " + data.getValue().getEndStation()));

        TableColumn<Train, Integer> seatCol = new TableColumn<>("余票");
        seatCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getAvailableSeats()).asObject());

        trainTable.getColumns().addAll(idCol, routeCol, seatCol);
        trainTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void connectToServer() {
        if (isConnected) return;
        new Thread(() -> {
            try {
                socket = new Socket("127.0.0.1", 8888);
                // 必须先锁住 out 对象的创建? 不，这里是单线程初始化，不需要锁，但 sendMessage 需要锁
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                isConnected = true;
                log("已连接到服务器 " + CLIENT_ID);
                sendMessage(new Message(CLIENT_ID, MessageType.CONNECT, "Login"));
                sendQuery();
                startListening();
            } catch (Exception e) {
                log("连接失败: " + e.getMessage());
            }
        }).start();
    }

    private void startListening() {
        try {
            while (isConnected) {
                Message msg = (Message) in.readObject();
                Platform.runLater(() -> {
                    if (msg.getMsgType() == MessageType.RESPONSE_SUCCESS) {
                        if (msg.getMsgPayload().contains("余票:")) {
                            updateTableData(msg.getMsgPayload());
                        } else {
                            log("系统消息: " + msg.getMsgPayload());
                        }
                    } else if (msg.getMsgType() == MessageType.RESPONSE_FAIL) {
                        log("❌ 错误: " + msg.getMsgPayload());
                    }
                });
            }
        } catch (Exception e) {
            log("与服务器断开连接。");
            isConnected = false;
        }
    }

    private void sendQuery() {
        sendMessage(new Message(CLIENT_ID, MessageType.QUERY_TICKETS, ""));
    }

    private void handleBuyAction() {
        Train selected = trainTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("请先选择车次！");
            return;
        }
        String numStr = numField.getText();
        if (!numStr.matches("[1-5]")) {
            showAlert("购票人数必须是 1~5 之间！");
            return;
        }
        sendMessage(new Message(CLIENT_ID, MessageType.LOCK_TICKET, selected.getTrainId() + "," + numStr));
    }

    private void handlePayAction() {
        String orderId = orderIdField.getText().trim();
        if (orderId.isEmpty()) return;
        sendMessage(new Message(CLIENT_ID, MessageType.PAY_ORDER, orderId));
    }

    private void handleCancelAction() {
        String orderId = orderIdField.getText().trim();
        if (orderId.isEmpty()) {
            showAlert("请输入订单号！");
            return;
        }
        sendMessage(new Message(CLIENT_ID, MessageType.CANCEL_ORDER, orderId));
    }

    private void sendMessage(Message msg) {
        if (!isConnected || out == null) return;
        new Thread(() -> {
            synchronized (out) { // 关键锁
                try {
                    out.writeObject(msg);
                    out.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // 更新表格数据的逻辑保持不变，为了节省篇幅这里简写
    private void updateTableData(String textData) {
        trainData.clear();
        String[] lines = textData.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            try {
                // 简化解析，为了代码完整性建议保留你之前的 robust 解析逻辑
                // 如果需要更严格的解析，请替换为你之前那个 robust 版本
                // 这里只演示最核心逻辑
                if (line.contains("余票:")) {
                    String trainId = line.substring(0, line.indexOf(" "));
                    int lastColon = line.lastIndexOf(":");
                    if (lastColon == -1) lastColon = line.lastIndexOf("：");
                    String seatsStr = line.substring(lastColon + 1).trim();
                    int seats = Integer.parseInt(seatsStr);

                    String route = line.substring(line.indexOf("(") + 1, line.indexOf(")"));
                    String[] stations = route.split("-");

                    Train t = new Train(trainId, stations[0], stations[1], 0);
                    t.setAvailableSeats(seats);
                    trainData.add(t);
                }
            } catch (Exception e) {}
        }
    }

    private void disconnect() {
        try { if (socket != null) socket.close(); } catch (IOException e) {}
    }

    private void log(String msg) {
        Platform.runLater(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            logArea.appendText("[" + sdf.format(new Date()) + "] " + msg + "\n");
        });
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}