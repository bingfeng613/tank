package com.tankWar.lobby;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;

public class SelectRoomWindow {
    private String roomNum;//选择的房间号
    private PasswordField Password;
    private Label label;
    private HBox hBox;
    private VBox vBox;
    private Button button;
    private Stage selectroomstage;

    private Socket socket=new Socket();
    DataOutputStream out = null;

    public SelectRoomWindow(Socket s,String a) throws IOException {
        this.socket = s;
        roomNum=a;
        out = new DataOutputStream(socket.getOutputStream());
    }

    public void ShowWindow(){
        Password=new PasswordField();
        label=new Label("房间密码");
        hBox=new HBox(label,Password);
        button=new Button("确认");
        vBox=new VBox(hBox,button);
        vBox.setAlignment(Pos.CENTER);
        button.setOnAction(event -> {
            String s =Password.getText();
            try {
                out.writeUTF("password|"+roomNum+"|"+s);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        selectroomstage=new Stage();
        selectroomstage.setTitle("输入房间密码");
        selectroomstage.setScene(new Scene(vBox));
        selectroomstage.show();

    }
    public boolean isShowing(){
        return selectroomstage.isShowing();
    }

    public void CloseWindow(){
        selectroomstage.close();
    }

}
