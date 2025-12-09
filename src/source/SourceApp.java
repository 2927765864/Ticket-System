package source;

import common.Message;
import common.MessageType;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 票源系统图形化界面
 * 职责：管理员操作界面，用于录入车次和释放票源
 */
public class SourceApp extends Application {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isConnected = false;
    private TextArea logArea;

    // 输入框
    private TextField trainIdField;
    private TextField startStationField;
    private TextField endStationField;
    private TextField seatsField;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("12306 票源管理系统 (管理员)");

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        // 1. 标题
        Label title = new Label("🚄 车次调度控制台");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // 2. 表单区域
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);

        trainIdField = new TextField("G999");
        startStationField = new TextField("深圳");
        endStationField = new TextField("长沙");
        seatsField = new TextField("500");

        grid.add(new Label("车次号:"), 0, 0);
        grid.add(trainIdField, 1, 0);
        grid.add(new Label("始发站:"), 0, 1);
        grid.add(startStationField, 1, 1);
        grid.add(new Label("终到站:"), 0, 2);
        grid.add(endStationField, 1, 2);
        grid.add(new Label("票源数量:"), 0, 3);
        grid.add(seatsField, 1, 3);

        // 3. 按钮
        Button btnAdd = new Button("发布 / 更新车源");
        btnAdd.setStyle("-fx-background-color: #1890ff; -fx-text-fill: white; -fx-font-size: 14px;");
        btnAdd.setPrefWidth(200);
        btnAdd.setOnAction(e -> sendAddTrainRequest());

        // 4. 日志区
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(150);
        logArea.setPromptText("系统连接日志...");

        root.getChildren().addAll(title, grid, btnAdd, new Separator(), new Label("操作日志:"), logArea);

        // 关闭窗口断开连接
        primaryStage.setOnCloseRequest(e -> disconnect());

        Scene scene = new Scene(root, 400, 500);
        primaryStage.setScene(scene);
        primaryStage.show();

        // 自动连接
        connect();
    }

    // --- 网络逻辑 ---

    private void connect() {
        new Thread(() -> {
            try {
                socket = new Socket("127.0.0.1", 8888);
                // 必须先创建 Output
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                isConnected = true;

                log("已连接到票务中心。");
                sendMessage(new Message("Source-Admin", MessageType.CONNECT, "Admin"));

                // 启动监听
                while (isConnected) {
                    Message msg = (Message) in.readObject();
                    Platform.runLater(() -> {
                        log("Server回复: " + msg.getMsgPayload());
                    });
                }
            } catch (Exception e) {
                log("连接断开或失败: " + e.getMessage());
                isConnected = false;
            }
        }).start();
    }

    private void sendAddTrainRequest() {
        if (!isConnected) {
            showAlert("未连接服务器！");
            return;
        }

        String id = trainIdField.getText().trim();
        String start = startStationField.getText().trim();
        String end = endStationField.getText().trim();
        String seats = seatsField.getText().trim();

        if (id.isEmpty() || start.isEmpty() || end.isEmpty() || seats.isEmpty()) {
            showAlert("请填写完整信息！");
            return;
        }

        // 格式: 车次,始发,终到,票数
        String payload = String.format("%s,%s,%s,%s", id, start, end, seats);
        sendMessage(new Message("Source-Admin", MessageType.ADD_TRAIN, payload));
    }

    private void sendMessage(Message msg) {
        if (!isConnected) return;
        new Thread(() -> {
            // 【关键】加上 synchronized 锁，防止并发写入导致流损坏
            synchronized (out) {
                try {
                    out.writeObject(msg);
                    out.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    log("发送失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private void disconnect() {
        try { if (socket != null) socket.close(); } catch (IOException e) { e.printStackTrace(); }
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