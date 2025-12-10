package client;

import common.Message;
import common.MessageType;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDate;

public class ClientApp extends Application {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isConnected = false;
    private final String CLIENT_ID = "Client-" + (int)(Math.random() * 1000);

    // 数据源
    private ObservableList<Train> trainData = FXCollections.observableArrayList();
    private ObservableList<LocalOrder> orderData = FXCollections.observableArrayList();

    // UI 组件
    private TableView<Train> trainTable;
    private TableView<LocalOrder> orderTable;
    private TextArea logArea;
    private DatePicker datePicker;
    private ComboBox<String> seatTypeCombo;
    private TextField numField;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("12306 购票终端 [" + CLIENT_ID + "]");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // 1. 顶部：连接与查询
        HBox topBox = new HBox(10);
        topBox.setPadding(new Insets(0, 0, 10, 0));
        Button btnConnect = new Button("连接服务器");
        btnConnect.setOnAction(e -> connectToServer());
        Button btnRefresh = new Button("查询车票");
        btnRefresh.setOnAction(e -> sendQuery());
        topBox.getChildren().addAll(btnConnect, btnRefresh);
        root.setTop(topBox);

        // 2. 中间 SplitPane
        SplitPane centerSplit = new SplitPane();
        centerSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // 车票列表
        VBox trainBox = new VBox(5);
        trainBox.getChildren().add(new Label("车次列表 (选中一行抢票):"));
        trainTable = new TableView<>();
        setupTrainTable(); // 初始化表格列
        trainTable.setItems(trainData);
        trainBox.getChildren().add(trainTable);

        // 订单列表
        VBox orderBox = new VBox(5);
        orderBox.getChildren().add(new Label("我的订单 (实时状态监控):"));
        orderTable = new TableView<>();
        setupOrderTable();
        orderTable.setItems(orderData);
        orderBox.getChildren().add(orderTable);

        centerSplit.getItems().addAll(trainBox, orderBox);
        centerSplit.setDividerPositions(0.5);
        root.setCenter(centerSplit);

        // 3. 底部
        VBox bottomBox = new VBox(10);
        bottomBox.setPadding(new Insets(10, 0, 0, 0));

        // 参数选择
        HBox paramBox = new HBox(10);
        datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(120);
        seatTypeCombo = new ComboBox<>();
        seatTypeCombo.getItems().addAll("二等座", "一等座", "商务座");
        seatTypeCombo.getSelectionModel().selectFirst();
        numField = new TextField("1");
        numField.setPrefWidth(50);
        Button btnBuy = new Button("立即抢票");
        btnBuy.setStyle("-fx-background-color: #ff4d4f; -fx-text-fill: white;");
        btnBuy.setOnAction(e -> handleBuyAction());
        paramBox.getChildren().addAll(new Label("日期:"), datePicker, new Label("席位:"), seatTypeCombo, new Label("人数:"), numField, btnBuy);

        // 订单操作
        HBox actionBox = new HBox(10);
        Button btnPay = new Button("支付选中订单");
        btnPay.setStyle("-fx-background-color: #52c41a; -fx-text-fill: white;");
        btnPay.setOnAction(e -> handleOrderAction(true));
        Button btnCancel = new Button("取消选中订单");
        btnCancel.setStyle("-fx-background-color: #faad14; -fx-text-fill: white;");
        btnCancel.setOnAction(e -> handleOrderAction(false));
        actionBox.getChildren().addAll(btnPay, btnCancel);

        logArea = new TextArea();
        logArea.setPrefHeight(80);
        logArea.setEditable(false);

        bottomBox.getChildren().addAll(new Separator(), paramBox, actionBox, new Label("日志:"), logArea);
        root.setBottom(bottomBox);

        primaryStage.setOnCloseRequest(e -> disconnect());
        Scene scene = new Scene(root, 650, 700);
        primaryStage.setScene(scene);
        primaryStage.show();

        connectToServer();
    }

    // --- 表格初始化 (仅保留这一个版本) ---
    private void setupTrainTable() {
        TableColumn<Train, String> idCol = new TableColumn<>("车次");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTrainId()));

        TableColumn<Train, String> routeCol = new TableColumn<>("区间");
        // 我们在解析时把 "北京-上海" 存入了 startStation 字段 (临时借用)
        routeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStartStation()));

        TableColumn<Train, String> infoCol = new TableColumn<>("余票详情");
        // 我们在解析时把 "{二等座=100...}" 存入了 endStation 字段 (临时借用)
        infoCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEndStation()));

        trainTable.getColumns().clear();
        trainTable.getColumns().addAll(idCol, routeCol, infoCol);
        trainTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void setupOrderTable() {
        TableColumn<LocalOrder, String> idCol = new TableColumn<>("订单号");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().orderId));
        TableColumn<LocalOrder, String> tCol = new TableColumn<>("车次");
        tCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().trainId));
        TableColumn<LocalOrder, String> dCol = new TableColumn<>("详情");
        dCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().detail));
        TableColumn<LocalOrder, String> sCol = new TableColumn<>("状态");
        sCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status));

        sCol.setCellFactory(c -> new TableCell<LocalOrder,String>(){
            @Override protected void updateItem(String item, boolean empty){
                super.updateItem(item, empty);
                if(empty||item==null){setText(null);setStyle("");}
                else{
                    setText(item);
                    if(item.equals("PENDING")) setStyle("-fx-text-fill:orange; -fx-font-weight:bold;");
                    else if(item.equals("PAID")) setStyle("-fx-text-fill:green; -fx-font-weight:bold;");
                    else setStyle("-fx-text-fill:gray;");
                }
            }
        });

        orderTable.getColumns().addAll(idCol, tCol, dCol, sCol);
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    // --- 网络与逻辑 ---
    private void connectToServer() {
        if(isConnected) return;
        new Thread(() -> {
            try {
                socket = new Socket("127.0.0.1", 8888);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                isConnected = true;
                sendMessage(new Message(CLIENT_ID, MessageType.CONNECT, "Login"));
                sendQuery(); // 连上立刻查票

                while(isConnected) {
                    Message msg = (Message) in.readObject();
                    Platform.runLater(() -> handleMessage(msg));
                }
            } catch(Exception e) { log("连接断开"); isConnected=false; }
        }).start();
    }

    private void handleMessage(Message msg) {
        if (msg.getMsgType() == MessageType.RESPONSE_SUCCESS) {
            String content = msg.getMsgPayload();
            if (content.contains("订单信息:")) { // 锁票成功
                parseAndAddOrder(content);
            } else if (content.contains("{") || content.contains("[该日期无票]")) { // 查票结果
                updateTrainList(content);
            } else {
                log("系统: " + content);
            }
        } else if (msg.getMsgType() == MessageType.ORDER_UPDATE) {
            String[] parts = msg.getMsgPayload().split(",");
            if(parts.length >= 2) updateOrderStatus(parts[0], parts[1]);
        } else if (msg.getMsgType() == MessageType.RESPONSE_FAIL) {
            showAlert("❌ " + msg.getMsgPayload());
        }
    }

    private void sendQuery() {
        String date = datePicker.getValue() != null ? datePicker.getValue().toString() : "";
        sendMessage(new Message(CLIENT_ID, MessageType.QUERY_TICKETS, date));
    }

    private void handleBuyAction() {
        Train selected = trainTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("请先选车次"); return; }

        String payload = String.format("%s,%s,%s,%s",
                selected.getTrainId(), numField.getText(), datePicker.getValue(), seatTypeCombo.getValue());
        sendMessage(new Message(CLIENT_ID, MessageType.LOCK_TICKET, payload));
    }

    private void updateTrainList(String data) {
        trainData.clear();
        String[] lines = data.split("\n");
        for (String line : lines) {
            try {
                // 解析格式: G101 (北京-上海) {二等座=100...}
                if (!line.contains("(")) continue;

                String id = line.split(" ")[0];
                String route = line.substring(line.indexOf("(") + 1, line.indexOf(")"));
                String seats;
                if (line.contains("{")) {
                    seats = line.substring(line.indexOf("{"));
                } else {
                    seats = "无票";
                }

                // 借用 Train 的构造函数来存放 UI 显示数据
                // id -> trainId, route -> startStation, seats -> endStation
                Train t = new Train(id, route, seats);
                trainData.add(t);
            } catch(Exception e) {}
        }
    }

    private void parseAndAddOrder(String msg) {
        try {
            int s = msg.indexOf("[订单")+3;
            int e = msg.indexOf("]", s);
            String oid = msg.substring(s, e);

            // 简单解析车次ID (从msg中查找)
            String tid = "未知车次";
            if (msg.contains("G")) {
                int idx = msg.indexOf("G");
                // 简单尝试截取车次
                if(msg.length() > idx+4) tid = msg.substring(idx, idx+4);
            } else if (msg.contains("D") || msg.contains("K") || msg.contains("T")) {
                // 模糊匹配
                tid = "车次";
            }

            String detail = datePicker.getValue() + " " + seatTypeCombo.getValue() + " " + numField.getText() + "张";
            orderData.add(0, new LocalOrder(oid, tid, detail, "PENDING"));
        } catch (Exception e) {
            log("订单解析失败，请查看日志");
        }
    }

    private void updateOrderStatus(String oid, String status) {
        for(LocalOrder o : orderData) {
            if(o.orderId.equals(oid)) {
                o.status = status;
                orderTable.refresh();
                break;
            }
        }
    }

    private void handleOrderAction(boolean isPay) {
        LocalOrder o = orderTable.getSelectionModel().getSelectedItem();
        if(o==null) return;
        sendMessage(new Message(CLIENT_ID, isPay?MessageType.PAY_ORDER:MessageType.CANCEL_ORDER, o.orderId));
    }

    private void sendMessage(Message msg) {
        if(!isConnected) return;
        new Thread(()->{
            try {
                synchronized(out){ out.writeObject(msg); out.flush(); }
            } catch(Exception e){}
        }).start();
    }

    private void log(String s) { Platform.runLater(()->logArea.appendText(s+"\n")); }
    private void showAlert(String s) { Platform.runLater(()->new Alert(Alert.AlertType.WARNING, s).showAndWait()); }
    private void disconnect() { try{if(socket!=null)socket.close();}catch(Exception e){} }

    // 内部类：用于订单显示
    public static class LocalOrder {
        String orderId; String trainId; String detail; String status;
        public LocalOrder(String id, String t, String d, String s) { orderId=id; trainId=t; detail=d; status=s; }
        public String getOrderId() { return orderId; }
        public String getTrainId() { return trainId; }
        public String getDetail() { return detail; }
        public String getStatus() { return status; }
    }
}