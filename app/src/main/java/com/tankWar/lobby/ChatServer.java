package com.tankWar.lobby;


import java.io.IOException;
import java.net.*;

class ChatServer extends Thread {
    ServerSocket serverSocket = null; // 创建服务器端套接字
    public boolean bServerIsRunning = false;
    InetAddress serverAddress=InetAddress.getLocalHost(); //获取本地主机的IP地址
    public ChatServer() throws UnknownHostException {
        try {
            serverSocket = new ServerSocket(8888); // 启动服务
            bServerIsRunning = true;
            System.out.println("服务器名称:"+serverAddress.getHostName());
            System.out.println("服务器IP:"+serverAddress.getHostAddress());
            System.out.println("服务器端口:" + 8888);
            System.out.println("服务器正在运行中...");
            while (true) {
                Socket socket = serverSocket.accept(); // 监听客户端的连接请求，并返回客户端socket
                new ServerProcess(socket).start(); // 创建一个新线程来处理与该客户的通讯
            }
        } catch (BindException e) {
            System.out.println("端口使用中....");
            System.out.println("请关掉相关程序并重新运行服务器！");
            System.exit(0);
        } catch (IOException e) {
            System.out.println("[ERROR] Cound not start server." + e);
        }
    }

    public static void main(String args[]) {
        try {
            new ChatServer();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}