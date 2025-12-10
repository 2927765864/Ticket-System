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
import java.time.LocalDate;
import java.util.Date;

public class SourceApp extends Application {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isConnected = false;
    private TextArea logArea;

    // 输入框
    private TextField trainIdField, startStationField, endStationField, seatsField;
    private DatePicker datePicker;
    private ComboBox<String> seatTypeCombo;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("12306 票源管理系统 (管理员)");

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Label title = new Label("🚄 车次调度控制台");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // 表单区域
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);

        trainIdField = new TextField("G999");
        startStationField = new TextField("深圳");
        endStationField = new TextField("长沙");

        datePicker = new DatePicker(LocalDate.now());

        seatTypeCombo = new ComboBox<>();
        seatTypeCombo.getItems().addAll("二等座", "一等座", "商务座");
        seatTypeCombo.getSelectionModel().selectFirst();

        seatsField = new TextField("500");

        grid.add(new Label("车次号:"), 0, 0); grid.add(trainIdField, 1, 0);
        grid.add(new Label("始发站:"), 0, 1); grid.add(startStationField, 1, 1);
        grid.add(new Label("终到站:"), 0, 2); grid.add(endStationField, 1, 2);
        grid.add(new Label("日期:"), 0, 3);   grid.add(datePicker, 1, 3);
        grid.add(new Label("席位:"), 0, 4);   grid.add(seatTypeCombo, 1, 4);
        grid.add(new Label("放票数:"), 0, 5); grid.add(seatsField, 1, 5);

        Button btnAdd = new Button("确认放票");
        btnAdd.setStyle("-fx-background-color: #1890ff; -fx-text-fill: white; -fx-font-size: 14px;");
        btnAdd.setPrefWidth(200);
        btnAdd.setOnAction(e -> sendAddTrainRequest());

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(150);

        root.getChildren().addAll(title, grid, btnAdd, new Separator(), new Label("操作日志:"), logArea);

        primaryStage.setOnCloseRequest(e -> disconnect());
        Scene scene = new Scene(root, 400, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        connect();
    }

    private void connect() {
        new Thread(() -> {
            try {
                socket = new Socket("127.0.0.1", 8888);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                isConnected = true;
                log("已连接到票务中心。");
                sendMessage(new Message("Source-Admin", MessageType.CONNECT, "Admin"));
                while (isConnected) {
                    Message msg = (Message) in.readObject();
                    Platform.runLater(() -> log("Server回复: " + msg.getMsgPayload()));
                }
            } catch (Exception e) {
                log("连接断开: " + e.getMessage());
                isConnected = false;
            }
        }).start();
    }

    private void sendAddTrainRequest() {
        if (!isConnected) { showAlert("未连接服务器！"); return; }

        String payload = String.format("%s,%s,%s,%s,%s,%s",
                trainIdField.getText(), startStationField.getText(), endStationField.getText(),
                datePicker.getValue(), seatTypeCombo.getValue(), seatsField.getText());

        sendMessage(new Message("Source-Admin", MessageType.ADD_TRAIN, payload));
    }

    private void sendMessage(Message msg) {
        if (!isConnected) return;
        new Thread(() -> {
            synchronized (out) {
                try { out.writeObject(msg); out.flush(); } catch (IOException e) { e.printStackTrace(); }
            }
        }).start();
    }

    private void disconnect() { try { if (socket != null) socket.close(); } catch (IOException e) {} }

    private void log(String msg) {
        Platform.runLater(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            logArea.appendText("[" + sdf.format(new Date()) + "] " + msg + "\n");
        });
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}