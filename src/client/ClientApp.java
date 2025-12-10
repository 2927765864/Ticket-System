package client;

import common.Message;
import common.MessageType;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
    private ObservableList<TrainViewModel> trainData = FXCollections.observableArrayList();
    private ObservableList<LocalOrder> orderData = FXCollections.observableArrayList();

    // UI 组件
    private TableView<TrainViewModel> trainTable;
    private TableView<LocalOrder> orderTable;
    private TextArea logArea;

    // 控件
    private DatePicker datePicker;      // 放在顶部，作为全局筛选
    private ComboBox<String> seatCombo; // 放在底部，作为购票参数
    private TextField numField;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("12306 购票终端 [" + CLIENT_ID + "]");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // ==========================================
        // 1. 顶部区域：连接 + 核心筛选 (日期)
        // ==========================================
        HBox topBox = new HBox(15);
        topBox.setPadding(new Insets(0, 0, 10, 0));
        topBox.setAlignment(Pos.CENTER_LEFT);

        Button btnConnect = new Button("连接服务器");
        btnConnect.setStyle("-fx-background-color: #1890ff; -fx-text-fill: white;");
        btnConnect.setOnAction(e -> connectToServer());

        // 日期选择器 (默认今天)
        datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(120);
        // 选中日期后，虽然可以自动刷新，但为了防止频繁请求，建议配合查询按钮使用
        // datePicker.setOnAction(e -> sendQuery());

        Button btnQuery = new Button("🔍 查询余票");
        btnQuery.setStyle("-fx-font-weight: bold;");
        btnQuery.setOnAction(e -> sendQuery());

        topBox.getChildren().addAll(btnConnect, new Label("出发日期:"), datePicker, btnQuery);
        root.setTop(topBox);

        // ==========================================
        // 2. 中间区域：车次表 & 订单表
        // ==========================================
        SplitPane centerSplit = new SplitPane();
        centerSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // 2.1 车次列表
        VBox trainBox = new VBox(5);
        trainBox.getChildren().add(new Label("车次列表 (请选择一趟列车):"));
        trainTable = new TableView<>();
        setupTrainTable();
        trainTable.setItems(trainData);
        VBox.setVgrow(trainTable, Priority.ALWAYS); // 让表格填满空间
        trainBox.getChildren().add(trainTable);

        // 2.2 我的订单
        VBox orderBox = new VBox(5);
        orderBox.getChildren().add(new Label("我的订单 (实时状态监控):"));
        orderTable = new TableView<>();
        setupOrderTable();
        orderTable.setItems(orderData);
        VBox.setVgrow(orderTable, Priority.ALWAYS);
        orderBox.getChildren().add(orderTable);

        centerSplit.getItems().addAll(trainBox, orderBox);
        centerSplit.setDividerPositions(0.6); // 车次表占60%高度
        root.setCenter(centerSplit);

        // ==========================================
        // 3. 底部区域：购票操作 & 订单操作
        // ==========================================
        VBox bottomBox = new VBox(10);
        bottomBox.setPadding(new Insets(10, 0, 0, 0));

        // 3.1 购票参数行
        HBox buyBox = new HBox(15);
        buyBox.setAlignment(Pos.CENTER_LEFT);

        seatCombo = new ComboBox<>();
        seatCombo.getItems().addAll("二等座", "一等座", "商务座", "硬座", "硬卧", "软卧", "无座");
        seatCombo.getSelectionModel().selectFirst();
        seatCombo.setPrefWidth(100);

        numField = new TextField("1");
        numField.setPrefWidth(50);

        Button btnBuy = new Button("立即抢票");
        btnBuy.setStyle("-fx-background-color: #ff4d4f; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnBuy.setOnAction(e -> handleBuyAction());

        buyBox.getChildren().addAll(
                new Label("席位类型:"), seatCombo,
                new Label("乘车人数:"), numField,
                btnBuy
        );

        // 3.2 订单操作行
        HBox orderActionBox = new HBox(15);
        orderActionBox.setAlignment(Pos.CENTER_LEFT);

        Button btnPay = new Button("支付选中订单");
        btnPay.setStyle("-fx-background-color: #52c41a; -fx-text-fill: white;");
        btnPay.setOnAction(e -> handleOrderAction(true));

        Button btnCancel = new Button("取消/退票");
        btnCancel.setStyle("-fx-background-color: #faad14; -fx-text-fill: white;");
        btnCancel.setOnAction(e -> handleOrderAction(false));

        orderActionBox.getChildren().addAll(btnPay, btnCancel);

        // 3.3 日志
        logArea = new TextArea();
        logArea.setPrefHeight(60); // 日志可以矮一点
        logArea.setEditable(false);
        logArea.setWrapText(true);

        bottomBox.getChildren().addAll(new Separator(), buyBox, orderActionBox, new Label("系统日志:"), logArea);
        root.setBottom(bottomBox);

        // ==========================================
        // 启动逻辑
        // ==========================================
        primaryStage.setOnCloseRequest(e -> disconnect());
        Scene scene = new Scene(root, 700, 750);
        primaryStage.setScene(scene);
        primaryStage.show();

        // 自动连接
        connectToServer();
    }

    // --- 表格设置 ---

    private void setupTrainTable() {
        TableColumn<TrainViewModel, String> idCol = new TableColumn<>("车次");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getId()));
        idCol.setPrefWidth(80);

        TableColumn<TrainViewModel, String> routeCol = new TableColumn<>("区间");
        routeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRoute()));
        routeCol.setPrefWidth(120);

        TableColumn<TrainViewModel, String> infoCol = new TableColumn<>("在该日期的余票详情");
        infoCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSeatsInfo()));

        trainTable.getColumns().addAll(idCol, routeCol, infoCol);
        trainTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void setupOrderTable() {
        TableColumn<LocalOrder, String> idCol = new TableColumn<>("订单号");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().orderId));

        TableColumn<LocalOrder, String> infoCol = new TableColumn<>("订单详情");
        infoCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().detail));

        TableColumn<LocalOrder, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status));
        statusCol.setCellFactory(column -> new TableCell<LocalOrder, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    if ("PENDING".equals(item)) setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                    else if ("PAID".equals(item)) setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    else setStyle("-fx-text-fill: gray;");
                }
            }
        });

        orderTable.getColumns().addAll(idCol, infoCol, statusCol);
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    // --- 业务逻辑 ---

    private void sendQuery() {
        if (!isConnected) return;
        // 获取顶部选择的日期，发送给服务器
        String selectedDate = datePicker.getValue().toString();
        log("正在查询 " + selectedDate + " 的车票...");
        sendMessage(new Message(CLIENT_ID, MessageType.QUERY_TICKETS, selectedDate));
    }

    private void handleBuyAction() {
        TrainViewModel selected = trainTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("请先在上方表格中选中一趟车次！");
            return;
        }

        // 关键：使用的是顶部选择的日期
        String date = datePicker.getValue().toString();
        String seat = seatCombo.getValue();
        String num = numField.getText();

        if (!num.matches("[1-5]")) { showAlert("购票人数限制 1~5 人"); return; }

        // 发送格式: "车次,人数,日期,席位"
        String payload = String.format("%s,%s,%s,%s", selected.getId(), num, date, seat);
        log("发起抢票: " + selected.getId() + " (" + seat + " x" + num + ")");
        sendMessage(new Message(CLIENT_ID, MessageType.LOCK_TICKET, payload));
    }

    private void handleMessage(Message msg) {
        if (msg.getMsgType() == MessageType.RESPONSE_SUCCESS) {
            String content = msg.getMsgPayload();
            // 根据内容判断是查询结果还是购票结果
            if (content.contains("订单信息:")) {
                parseAndAddOrder(content); // 购票成功，解析订单
            } else if (content.contains("{") || content.contains("[该日期无票]")) {
                updateTrainList(content);  // 查询结果，刷新表格
                log("车票列表已刷新。");
            } else {
                log("系统提示: " + content);
            }
        } else if (msg.getMsgType() == MessageType.ORDER_UPDATE) {
            // 推送更新
            String[] parts = msg.getMsgPayload().split(",");
            if (parts.length >= 2) updateOrderStatus(parts[0], parts[1]);
        } else if (msg.getMsgType() == MessageType.RESPONSE_FAIL) {
            showAlert("❌ " + msg.getMsgPayload());
        }
    }

    // 解析服务器返回的 List<Train> 字符串
    private void updateTrainList(String data) {
        trainData.clear();
        String[] lines = data.split("\n");
        for (String line : lines) {
            try {
                // 格式: G101 (北京-上海) {二等座=100...}
                if (!line.contains("(")) continue;

                String id = line.split(" ")[0];
                String route = line.substring(line.indexOf("(") + 1, line.indexOf(")"));
                String seatsInfo;
                if (line.contains("{")) {
                    seatsInfo = line.substring(line.indexOf("{"));
                } else {
                    seatsInfo = "该日无票";
                }

                trainData.add(new TrainViewModel(id, route, seatsInfo));
            } catch (Exception e) {}
        }
    }

    private void parseAndAddOrder(String msg) {
        try {
            int s = msg.indexOf("[订单") + 3;
            int e = msg.indexOf("]", s);
            String oid = msg.substring(s, e);

            // 构造一个本地显示的订单详情字符串
            String detail = String.format("%s %s (%s %s张)",
                    datePicker.getValue(), // 日期
                    trainTable.getSelectionModel().getSelectedItem().getId(), // 车次
                    seatCombo.getValue(), // 席位
                    numField.getText());  // 人数

            orderData.add(0, new LocalOrder(oid, detail, "PENDING"));
        } catch (Exception e) {
            log("订单创建成功，但解析显示出错，请查看日志。");
        }
    }

    private void updateOrderStatus(String oid, String status) {
        for (LocalOrder o : orderData) {
            if (o.orderId.equals(oid)) {
                o.status = status;
                orderTable.refresh();
                if ("PAID".equals(status)) log("✅ 订单 " + oid + " 支付成功！");
                if ("TIMEOUT".equals(status)) log("⚠️ 订单 " + oid + " 已超时失效。");
                if ("CANCELLED".equals(status)) log("🗑️ 订单 " + oid + " 已取消。");
                break;
            }
        }
    }

    // --- 基础通信与工具 ---

    private void connectToServer() {
        if (isConnected) return;
        new Thread(() -> {
            try {
                socket = new Socket("127.0.0.1", 8888);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                isConnected = true;
                sendMessage(new Message(CLIENT_ID, MessageType.CONNECT, "Login"));

                // 连上后，自动查询当前日期
                Platform.runLater(() -> sendQuery());

                while (isConnected) {
                    Message msg = (Message) in.readObject();
                    Platform.runLater(() -> handleMessage(msg));
                }
            } catch (Exception e) {
                log("连接失败或断开: " + e.getMessage());
                isConnected = false;
            }
        }).start();
    }

    private void handleOrderAction(boolean isPay) {
        LocalOrder o = orderTable.getSelectionModel().getSelectedItem();
        if (o == null) { showAlert("请先选中一个订单！"); return; }
        sendMessage(new Message(CLIENT_ID, isPay ? MessageType.PAY_ORDER : MessageType.CANCEL_ORDER, o.orderId));
    }

    private void sendMessage(Message msg) {
        if (!isConnected) return;
        new Thread(() -> {
            synchronized (out) {
                try { out.writeObject(msg); out.flush(); } catch (IOException e) {}
            }
        }).start();
    }

    private void disconnect() { try { if (socket != null) socket.close(); } catch (IOException e) {} }
    private void log(String s) { Platform.runLater(() -> logArea.appendText(s + "\n")); }
    private void showAlert(String s) { Platform.runLater(() -> new Alert(Alert.AlertType.WARNING, s).showAndWait()); }

    // --- 内部数据模型类 ---

    // 专门用于车次表格显示的模型
    public static class TrainViewModel {
        private final SimpleStringProperty id = new SimpleStringProperty();
        private final SimpleStringProperty route = new SimpleStringProperty();
        private final SimpleStringProperty seatsInfo = new SimpleStringProperty();

        public TrainViewModel(String id, String route, String seatsInfo) {
            this.id.set(id);
            this.route.set(route);
            this.seatsInfo.set(seatsInfo);
        }
        public String getId() { return id.get(); }
        public String getRoute() { return route.get(); }
        public String getSeatsInfo() { return seatsInfo.get(); }
    }

    // 专门用于订单表格显示的模型
    public static class LocalOrder {
        String orderId; String detail; String status;
        public LocalOrder(String id, String d, String s) { orderId = id; detail = d; status = s; }
    }
}