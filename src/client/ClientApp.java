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

/**
 * 购票终端图形化界面
 * 对应课件：用户购票操作界面
 */
public class ClientApp extends Application {

    // 网络通信相关
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isConnected = false;
    private final String CLIENT_ID = "Client-" + (int)(Math.random() * 1000); // 随机生成一个ID

    // UI 组件
    private TableView<Train> trainTable;
    private ObservableList<Train> trainData = FXCollections.observableArrayList();
    private TextArea logArea;
    private TextField numField; // 购票数量输入框
    private TextField orderIdField; // 订单号输入框 (用于支付)

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("12306 购票终端 [" + CLIENT_ID + "]");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // 1. 顶部：操作栏 (刷新 & 状态)
        HBox topBox = new HBox(10);
        topBox.setPadding(new Insets(0, 0, 10, 0));
        Button btnConnect = new Button("连接服务器");
        btnConnect.setOnAction(e -> connectToServer());
        Button btnRefresh = new Button("刷新车票");
        btnRefresh.setOnAction(e -> sendQuery());
        topBox.getChildren().addAll(btnConnect, btnRefresh);
        root.setTop(topBox);

        // 2. 中间：车票表格
        trainTable = new TableView<>();
        setupTableColumns();
        trainTable.setItems(trainData);
        root.setCenter(trainTable);

        // 3. 底部：购票与日志区
        VBox bottomBox = new VBox(10);
        bottomBox.setPadding(new Insets(10, 0, 0, 0));

        // 3.1 购票操作行
        HBox buyBox = new HBox(10);
        numField = new TextField("1");
        numField.setPrefWidth(50);
        numField.setPromptText("张数");
        Button btnBuy = new Button("立即抢票");
        btnBuy.setStyle("-fx-background-color: #ff4d4f; -fx-text-fill: white; -fx-font-weight: bold;");
        btnBuy.setOnAction(e -> handleBuyAction());
        buyBox.getChildren().addAll(new Label("购票人数(1-5):"), numField, btnBuy);

        // 3.2 支付操作行
        HBox payBox = new HBox(10);
        orderIdField = new TextField();
        orderIdField.setPromptText("输入订单号支付...");
        Button btnPay = new Button("支付订单");
        btnPay.setStyle("-fx-background-color: #52c41a; -fx-text-fill: white;");
        btnPay.setOnAction(e -> handlePayAction());
        payBox.getChildren().addAll(new Label("订单支付:"), orderIdField, btnPay);

        // 3.3 日志区
        logArea = new TextArea();
        logArea.setPrefHeight(120);
        logArea.setEditable(false);

        bottomBox.getChildren().addAll(new Separator(), buyBox, payBox, new Label("终端日志:"), logArea);
        root.setBottom(bottomBox);

        // 窗口关闭时断开连接
        primaryStage.setOnCloseRequest(e -> disconnect());

        Scene scene = new Scene(root, 500, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // 自动连接
        connectToServer();
    }

    // --- 表格列设置 ---
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

    // --- 网络连接逻辑 ---
    private void connectToServer() {
        if (isConnected) return;
        new Thread(() -> {
            try {
                socket = new Socket("127.0.0.1", 8888);
                // 必须先创建 Output
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                isConnected = true;

                log("已连接到服务器 " + CLIENT_ID);

                // 发送身份验证
                sendMessage(new Message(CLIENT_ID, MessageType.CONNECT, "Login"));

                // 自动刷新一次列表
                sendQuery();

                // 启动监听循环
                startListening();

            } catch (Exception e) {
                log("连接失败: " + e.getMessage());
            }
        }).start();
    }

    private void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- 监听服务器消息 ---
    private void startListening() {
        try {
            while (isConnected) {
                Message msg = (Message) in.readObject();

                // UI 更新必须在主线程
                Platform.runLater(() -> {
                    // 处理车次列表更新
                    if (msg.getMsgType() == MessageType.RESPONSE_SUCCESS) {
                        // 如果 payload 包含 "Train" 关键字或者看起来像列表数据，我们就尝试解析
                        // 偷懒做法：只要 payload 里有 "余票:"，就认为是车次列表，重新分割解析太麻烦
                        // 更好的做法是 Server 直接传 List<Train> 对象，但前面我们协议定的是 String payload
                        // 这里我们只显示日志，列表刷新依靠点击“刷新”时 Server 发回的特定格式

                        // 修正：我们在 Server/ClientHandler 里对 QUERY_TICKETS 是直接 toString() 拼的字符串
                        // 我们需要解析这个字符串来更新表格
                        if (msg.getMsgPayload().contains("余票:")) {
                            updateTableData(msg.getMsgPayload());
                        } else {
                            log("系统消息: " + msg.getMsgPayload());
                        }
                    } else if (msg.getMsgType() == MessageType.RESPONSE_FAIL) {
                        log("❌ 错误: " + msg.getMsgPayload());
                    } else if (msg.getMsgType() == MessageType.DATA_UPDATE) {
                        // 如果你有实现主动推送，这里处理
                    }
                });
            }
        } catch (Exception e) {
            log("与服务器断开连接。");
            isConnected = false;
        }
    }

    // --- 业务操作 ---

    // 1. 刷新查询
    private void sendQuery() {
        sendMessage(new Message(CLIENT_ID, MessageType.QUERY_TICKETS, ""));
    }

    // 2. 购买 (锁票)
    private void handleBuyAction() {
        Train selected = trainTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("请先在表格中选择一趟车次！");
            return;
        }
        String numStr = numField.getText();
        if (!numStr.matches("[1-5]")) {
            showAlert("购票人数必须是 1~5 之间！");
            return;
        }

        // 发送: 车次,数量
        String payload = selected.getTrainId() + "," + numStr;
        sendMessage(new Message(CLIENT_ID, MessageType.LOCK_TICKET, payload));
    }

    // 3. 支付
    private void handlePayAction() {
        String orderId = orderIdField.getText().trim();
        if (orderId.isEmpty()) {
            showAlert("请输入订单号！(请从日志中复制)");
            return;
        }
        sendMessage(new Message(CLIENT_ID, MessageType.PAY_ORDER, orderId));
    }

    // 发送消息辅助方法
    // 修复后的 sendMessage 方法 (client/ClientApp.java)
    private void sendMessage(Message msg) {
        if (!isConnected || out == null) {
            log("未连接服务器！");
            return;
        }

        // 使用新线程发送，防止卡死UI
        new Thread(() -> {
            // 【关键修改】加上 synchronized 锁
            // 确保同一时间只有一个线程在操作 out 流，防止数据写乱
            synchronized (out) {
                try {
                    out.writeObject(msg);
                    out.flush(); // 这一步也很重要
                    // System.out.println("DEBUG: 已发送 " + msg.getMsgType()); // 调试用
                } catch (IOException e) {
                    e.printStackTrace();
                    log("发送失败: " + e.getMessage());
                }
            }
        }).start();
    }

    // 解析 Server 发回来的文本数据并更新表格
    // 格式参考：G101 (北京-上海) 余票:200
    // 替换 client/ClientApp.java 中的 updateTableData 方法
    private void updateTableData(String textData) {
        trainData.clear();
        String[] lines = textData.split("\n");

        // 打印一下原始数据，看看服务器到底发了什么
        System.out.println("DEBUG: 收到原始数据 -> \n" + textData);

        for (String line : lines) {
            // 跳过空行
            if (line.trim().isEmpty()) continue;

            try {
                // 更加健壮的解析逻辑
                // 预期格式: G101 (北京-上海) 余票:200

                // 1. 解析车次 (取第一个空格前的部分)
                int firstSpace = line.indexOf(" ");
                if (firstSpace == -1) throw new Exception("找不到车次后的空格");
                String trainId = line.substring(0, firstSpace);

                // 2. 解析余票 (取最后一个冒号后的部分)
                int lastColon = line.lastIndexOf(":"); // 英文冒号
                if (lastColon == -1) {
                    lastColon = line.lastIndexOf("："); // 尝试兼容中文冒号
                }
                if (lastColon == -1) throw new Exception("找不到余票冒号");

                String seatsStr = line.substring(lastColon + 1).trim();
                int seats = Integer.parseInt(seatsStr);

                // 3. 解析站点 (取括号中间的内容)
                int startBracket = line.indexOf("(");
                int endBracket = line.indexOf(")");
                if (startBracket == -1 || endBracket == -1) throw new Exception("找不到站点括号");

                String route = line.substring(startBracket + 1, endBracket);
                String[] stations = route.split("-");
                if (stations.length < 2) throw new Exception("站点格式错误(应用横杠-分隔)");

                // 4. 构造对象并添加到表格
                Train t = new Train(trainId, stations[0], stations[1], 0);
                t.setAvailableSeats(seats);
                trainData.add(t);

            } catch (Exception e) {
                // 关键修改：如果有解析错误，直接显示在日志区，方便调试！
                String err = "解析失败 [" + line + "]: " + e.getMessage();
                System.err.println(err);
                log("⚠️ " + err);
            }
        }
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