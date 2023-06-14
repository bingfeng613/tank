package com.tankWar.server;

import javafx.util.Pair;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.Date;


//接收到客户端socket发来的信息后进行解析、处理、转发
public class Main {
    // 连接相关
    // 参考代码: https://blog.csdn.net/hao134838/article/details/113185058
    // https://www.cnblogs.com/binarylei/p/12643807.html
    ServerSocketChannel serverSocket = null;
    Selector selector = null;
    // 服务端监听端口
    int serverPort;

    /* 只在用户成功登陆后存储 */
    HashMap<SocketChannel, User> users = new HashMap<>();
    /* 只在房间成功创建后存储 */
    // 根据房间来进行索引
    HashMap<String, Room> rooms = new HashMap<>();

    // 数据操作函数
    DBOperator operator;

    public Main(int port){
        this.serverPort = port;
        // 初始化数据库操作函数
        operator = new DBOperator();
    }

    public Main(){
        this(Config.port);
    }

    public void start() {
        // 1. 启动服务端套接字
        try {
            // 启动服务
            serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress(serverPort));
            // 设置服务端Socket非阻塞
            serverSocket.configureBlocking(false);
            // 设置多路复用器 并注册服务端Socket
            selector = Selector.open();
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        } catch (BindException e) {
            System.out.println("[error] 端口使用中....");
            System.out.println("[error] 请关掉相关程序并重新运行服务器！");
            System.exit(0);
        } catch (IOException e) {
            System.out.println("[error] Cound not start server." + e);
        }

        // 2. 输出打印信息
//        System.out.println("[info] 服务器名称:"+serverAddress.getHostName());
//        System.out.println("[info] 服务器IP:" + serverAddress.getHostAddress());
        System.out.println("[info] 服务器端口:" + serverPort);
        System.out.println("[info] 服务器正在运行中...");

        // 3. 循环启动服务器线程
        while (true) {
            try {
                selector.select();

                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                System.out.println(selectionKeys.size());
                for (SelectionKey key : selectionKeys) {
                    if (key.isAcceptable()) {
                        // 如果轮询到服务Socket 则建立Socket连接
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel socket = server.accept();
                        // 如果读取到的socket为空 则跳过
//                        if(socket == null)
//                            continue;
                        socket.configureBlocking(false);
                        socket.register(selector, SelectionKey.OP_READ);
                        System.out.println("[info] 客户端连接成功!");
                    } else if (key.isReadable()) {
                        // 如果轮询到Socket 则处理接收连接
                        SocketChannel socketChannel = (SocketChannel) key.channel();
                        User user = users.get(socketChannel);

                        // 根据用户状态来选择处理对象
                        if (user == null || user.getStatus().isInLobby()) {
                            // 大厅处理对象
                            new LobbyHandler(socketChannel).handle();
                        } else if (user.getStatus().isInRoom()) {
                            new RoomHandler(socketChannel).handle();
                        } else if (user.getStatus().isPlaying()) {
                            new GameHandler(socketChannel, user.getRoom()).handle();
                        }
                    }

                    // 删除
                    selectionKeys.remove(key);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 大厅接收处理者
    class LobbyHandler extends FrontHandler{

        // 构造函数
        public LobbyHandler(SocketChannel socketChannel) {
            super(socketChannel);
        }

        // 用来处理大厅接收到的消息
        public void handle()  {
            // 1. 读取消息
            String text = receive();
            System.out.println(text);
            if(text == null)
                return;

            // 2. 解析对应的函数
            // 从服务器端接收一条信息后拆分、解析，并执行相应操作
            StringTokenizer st = new StringTokenizer(text, "|");
            String strKey = st.nextToken(), returnMsg = null;
            try {
                switch (strKey) {
                    /* 登陆和注册 */
                    case "register" -> returnMsg = register(st);
                    case "login" -> returnMsg = login(st);

                    /* 大厅内的操作 */
                    // 创建房间
                    case "Create" ->  createRoom(st);
                    // 处理用户选择房间的消息   客户端点击选择房间后传来的Select room
                    case "Select room" ->  returnMsg = selectRoom(st);
                    // 选择的房间若设置了密码，需要验证
                    case "password" -> returnMsg = validPassword(st);
                    // 在大厅中聊天
                    case "talk" -> returnMsg = talk(st);
                    // 刷新在线用户列表
                    case "init" -> sendAllUser();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 3. 发送返回消息
            if(returnMsg != null)
                this.send(returnMsg);
        }
        // 登录
        String login(StringTokenizer st) throws IOException {
            String account = st.nextToken();
            String password = st.nextToken().trim();

            // 判断该用户是否登陆过了
            for(User user: users.values()) {
                if (account.equals(user.getAccount())){
                    //不能重复登陆的提示
                    return "warning|double";
                }
            }

            Date t = new Date();
            System.out.println("[info] 用户 " + account + " 正在登陆..." + "  密码:" + password + t);

            // 判断用户名和密码 ->转为在数据库中寻找
            if (!operator.checkLogin(account, password)) {
                System.out.println("[error] 用户 "+ account + " 登陆失败！" + t);
                return "warning|" + account + "登陆失败，请检查您的输入!";
            }

            // 获取用户昵称
            String nickName = operator.getNickName(account);
            if (nickName == null) {
                return "warning|" + account + "登陆失败，请检查您的输入!";
            }

            // 保存用户信息 (只有当用户成功登陆时才添加线程)
            users.put(curSocket, new User(nickName, account));


            System.out.println("[info] 用户 " + account + " 登录成功，" + "登录时间:" + t);

            send("login|succeed" + "|" + nickName);
            sendToLobby("talk|>>>欢迎 " + nickName + " 进来与我们一起交谈!");
            sendAllUser();
            sendRooms();

            // 向客户端发送信息(登陆成功 + 欢迎语录 + 所有用户姓名)
            return null;
        }

        // 注册
        String register(StringTokenizer st)  {
            String name = st.nextToken();
            String account = st.nextToken();
            String password = st.nextToken();

            if (operator.isExistUserName(name)) {
                // 验证昵称是否重复
                System.out.println("[ERROR] " + name + " Register fail!");
                return "register|name";
            } else if (operator.isExistUser(account)) {
                // 验证账号重复
                System.out.println("[ERROR] " + account + " Register fail!");
                return "register|account";
            } else {
                // 创建用户
                boolean flag = operator.createPlayer(name, account, password);

                if (flag) {
                    System.out.println("[info] User " + name + " 注册成功");
                    return "register|success";
                }
            }

            return null;
        }

        // 创建房间,分有密码和无密码的情况
        void createRoom(StringTokenizer st) {
            // 验证是否存在密码
            String isPassword = st.nextToken();
            boolean havePassword = isPassword.equals("password");

            String userName = st.nextToken();
            String account = st.nextToken(); //传来的账号
            String roomName = st.nextToken();
            int userNum = Integer.parseInt(st.nextToken());

            // 创建房间 (对于房主来说 房号就是他的账号)
            Room room = new Room(account, roomName, userNum);
            if (havePassword) {
                String password = st.nextToken();
                room.setPassword(password);
            }

            // 添加房主的信息 并将房间添加进全局中
            User user = users.get(curSocket);
            room.addOnlineUser(curSocket, user);
            rooms.put(room.getRoomNum(), room);

            // 更新房间里面的信息，并刷新客户端的游戏大厅和游戏房间里的聊天框
            // 用户进入房间，房间里面的人员信息会增加
            // 用户进入房间，大厅里的房间人数会变
            sendToRoom(room, "roomTalk|>>> 欢迎 " + userName + " 加入房间");
            sendAllUsersToRoom(room);
            sendRooms();
        }

        // 进入房间
        String selectRoom(StringTokenizer st) {
            String returnMsg = null;

            // 获取到用户选择的房间号
            String roomNum = st.nextToken();
            for (Room room : rooms.values()) {
                if (room.getRoomNum().equals(roomNum)) {
                    if (room.isFull() || room.getStatus()) {
                        //房间达到人数上限或者房间正在游戏时，无法进入房间
                        returnMsg = "select room|failed";
                    } else if (room.havePassword()) {//提示有密码
                        returnMsg = "select room|password";
                    } else {
                        // 处理玩家和用户的之间的关联关系
                        User user = users.get(curSocket);
                        room.addOnlineUser(curSocket, user);
                        // 成功进入房间
                        returnMsg = "select room|success";
                        // 发送欢迎消息
                        sendToRoom(room, "roomTalk|>>>欢迎 " + user.getNickName() + " 加入房间");
                        // 用户进入房间，大厅里的房间人数会变
                        sendAllUsersToRoom(room);
                    }
                    break;
                }
            }

            // 返回消息
            return returnMsg;
        }

        // 验证进入房间的密码
        String validPassword(StringTokenizer st) {
            String returnMsg = null;

            //在获取一次房间号，因为有可能在输入密码期间有其他人进入房间了
            String roomNum = st.nextToken();
            String password = st.nextToken();
            for (Room room : rooms.values()) {
                if (room.getRoomNum().equals(roomNum)) {
                    if (room.isFull() || room.getStatus()) {
                        //房间达到人数上限或者房间正在游戏时，无法进入房间
                        returnMsg = "select room|failed";
                    } else if (!room.checkPassword(password)) {//密码错误
                        returnMsg = "select room|password error";
                    } else {
                        // 处理玩家和用户的之间的关联关系
                        User user = users.get(curSocket);
                        room.addOnlineUser(curSocket, user);

                        sendRooms();//用户进入房间，大厅里的房间人数会变
                        sendToRoom(room, "roomTalk|>>>欢迎 " + user.getNickName() + " 加入房间");

                        //成功进入房间
                        returnMsg = "select room|success";
                    }
                    break;
                }
            }

            return returnMsg;
        }

        // 大厅发言
        String talk(StringTokenizer st) throws IOException {
            String strTalkInfo = st.nextToken(); // 得到聊天内容;
            String strSender = st.nextToken(); // 得到发消息人d
            String strReceiver = st.nextToken(); // 得到接收人
            System.out.println("[TALK_" + strReceiver + "] " + strTalkInfo);
            Date t = new Date();

            // 得到当前时间
            GregorianCalendar calendar = new GregorianCalendar();
            String strTime = "(" + calendar.get(Calendar.HOUR) + ":"
                    + calendar.get(Calendar.MINUTE) + ":"
                    + calendar.get(Calendar.SECOND) + ")";
            strTalkInfo += strTime;

            // 记录事件
            System.out.println("[info] Constants.USER" + strSender + "对 " + strReceiver + "说:" + strTalkInfo
                    + t);

            if (strReceiver.equals("All")) {
                this.sendToLobby("talk|" + strSender + " 对所有人说：" + strTalkInfo);
            } else if (strSender.equals(strReceiver)) {
                return "talk|>>>不能自言自语哦!";
            } else {
                for(Map.Entry<SocketChannel, User> pair: users.entrySet()) {
                    User user = pair.getValue();
                    //更新接收方的消息
                    // todo 封装Write函数
                    if (strReceiver.equals(user.getNickName())) {
                        SocketChannel socket = pair.getKey();
                        String text = "talk|" + strSender + " 对你说：" + strTalkInfo;
                        socket.write(ByteBuffer.wrap(text.getBytes()));
                        //更新发送方的消息
                        return "talk|你对 " + strReceiver + "说：" + strTalkInfo;
                    }
                }
            }

            return null;
        }


    }

    // 房间处理
    class RoomHandler extends FrontHandler{
        // 构造函数
        public RoomHandler(SocketChannel socketChannel) {
            super(socketChannel);
        }

        // 用来处理大厅接收到的消息
        @Override
        public void handle() {
            // 1. 读取消息
            String text = super.receive();
            if(text == null)
                return;

            // 2. 解析对应的函数
            // 从服务器端接收一条信息后拆分、解析，并执行相应操作
            StringTokenizer st = new StringTokenizer(text, "|");
            String strKey = st.nextToken(), returnMsg = null;
            try {
                switch (strKey) {
                    /* 房间操作 */
                    // 有用户准备
                    case "isReady" -> userReady(st);
                    // 有用户取消准备
                    case "cancelReady" -> userCancelReady(st);
                    // 检查房间内的用户是否都准备好了
                    case "check status" -> returnMsg = checkIfAllReady();
//                        case "gameOver" ->
//                            // 游戏结束处理
//                                processGameOver();
                    // 退出房间
                    case "exitRoom" -> exitRoom();

                    /* 聊天功能 */
                    // 房间内发言
                    case "roomTalk" -> returnMsg = roomTalk(st);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 3. 发送返回消息
            if(returnMsg != null)
                this.send(returnMsg);
        }

        // 用户选择进行准备
        private void userReady(StringTokenizer st) {
            //准备的玩家
            String readyName = st.nextToken();
            //传递给客户端的房间在线用户\
            User user = users.get(curSocket);
            sendToRoom(user.getRoom(), "roomTalk|" + readyName + " 已准备");
            user.setStatus(UserStatus.Ready);
        }

        // 用户取消准备
        private void userCancelReady(StringTokenizer st) {
            //取消准备的玩家
            String readyName = st.nextToken();
            //传递给客户端的房间在线用户
            User user = users.get(curSocket);
            sendToRoom(user.getRoom(),"roomTalk|" + readyName + " 已取消准备");
            user.setStatus(UserStatus.NoReady);
        }

        // 验证是否全部用户准备好
        String checkIfAllReady() throws IOException {
            User user = users.get(curSocket);
            Room room = rooms.get(user.getAccount());

            // 切换房主状态
            user.setStatus(UserStatus.Ready);

            if (!room.checkAllUsersReady()) {
                user.setStatus(UserStatus.NoReady);
                return "begin game|failed";
            }

            // 用户全都准备好了 开始游戏  改变房间状态(对接游戏部分)
            room.startGame(true);


            System.out.println("[info] New game server");

            // 把游戏开始信息发送给房间内所有用户
            sendToRoom(room, "begin game|succeed");
            // 刷新大厅中这个房间的状态
            sendRooms();
            // 发送初始化信息
            new GameHandler(curSocket, room).sendInitMsg();

            return null;
        }

        // 退出房间
        void exitRoom() {
            User user = users.get(curSocket);
            Room room = rooms.get(user.getAccount());

            // 如果是房主退出了，则解散房间
            if (room.isHost(user.getNickName())) {
                //调用函数 清空房间内部的所有玩家 并修改状态
                room.cleanAll();
                //从总房间列表中将这个房间删除
                rooms.remove(room.getRoomNum());

                // 先要进行刷新  防止场景切换后出现问题  客户端需要新的Rooms列表进行大厅场景的切换
                sendRooms();
                // 向房间内的所有客户端发送 房间解散的信息
                sendToRoom(room, "Owner exitRoom|" + room.getRoomNum());
            }
            //普通用户退出该房间
            else {
                // 从房间中移除该用户 使用Room中的函数
                room.removeOnlineUser(curSocket);

                //用户退出房间，房间里面的人员信息会修改
                sendAllUsersToRoom(room);
                // 发送退出房间消息给其他用户
                sendToRoom(room,"roomTalk|>>>再见 " + user.getNickName() + " 退出房间");
            }

            sendRooms();//刷新大厅内的房间列表
        }



        // todo 在房间内部发言 (待优化)
        String roomTalk(StringTokenizer st) throws IOException {
            String strTalkInfo = st.nextToken(); // 得到聊天内容;
            String strSender = st.nextToken(); // 得到发消息人
            String strReceiver = st.nextToken(); // 得到接收人

            System.out.println("[TALK_" + strReceiver + "] " + strTalkInfo);
            Date t = new Date();

            // 得到当前时间
            GregorianCalendar calendar = new GregorianCalendar();
            String strTime = "(" + calendar.get(Calendar.HOUR) + ":"
                    + calendar.get(Calendar.MINUTE) + ":"
                    + calendar.get(Calendar.SECOND) + ")";
            strTalkInfo += strTime;

            //记录事件
            System.out.println("[info] Constants.USER" + strSender + "对 " + strReceiver + "说:" + strTalkInfo
                    + t);

            if (strReceiver.equals("All")) {
                Room room = users.get(curSocket).getRoom();
                sendToRoom(room,"roomTalk|" + strSender + " 对所有人说：" + strTalkInfo);
            } else  if (strSender.equals(strReceiver)) {
                return "roomTalk|>>>不能自言自语哦!";
            } else {
                // 获得接收方的
                Room room =  users.get(curSocket).getRoom();
                Pair<SocketChannel, User> recvPair = room.getUser(strReceiver);
                if(recvPair==null)
                    return null;

                //更新接收方的消息
                SocketChannel recvSocket = recvPair.getKey();
                String text = "talk|" + strSender + " 对你说：" + strTalkInfo;
                recvSocket.write(ByteBuffer.wrap(text.getBytes()));

                //更新发送方的消息
                return "talk|你对 " + strReceiver + "说：" + strTalkInfo;
            }

            return null;
        }
    }

    // 封装向大厅和房间发送消息的函数
    // 使其能够被Lobby/Room Handler共享
    abstract class FrontHandler extends Handler{
        public FrontHandler(SocketChannel socket) {
            super(socket);
        }

        // 向大厅发送信息
        protected void sendToLobby(String strSend) {
            for (SocketChannel socket: users.keySet()) {
                this.send(socket, strSend);
            }
        }

        // 刷新游戏大厅的在线房间
        protected void sendRooms() {
            String strOnline = "lobby";

            int i = 0;
            for (Room room : rooms.values()) {
                i++;
                strOnline += "|" + room.getRoomNum();//房间号
                strOnline += "|" + String.valueOf(i);//号码
                strOnline += "|" + room.getRoomName();//房间名
                strOnline += "|" + room.getHostName();//房主名字
                strOnline += "|" + String.valueOf(room.getOnlineUserNum());
                strOnline += "|" + String.valueOf(room.getMaxUserNum());
                strOnline += "|" + room.getStatus();//房间状态
            }
            sendToLobby(strOnline);

            System.out.println("[info] 在线人数:" + strOnline);
        }


        // 刷新大厅内的在线用户
        protected void sendAllUser() {
            String strOnline = "online";
            for (User user: users.values()) {
                strOnline += "|" + user.getNickName();
            }
            System.out.println("[info] 当前在线人数:" + users.size());

            sendToLobby(strOnline);
        }

        // 向给指定的Sockets发送信息
        protected void sendToRoom(Room room, String strSend) {
            for (SocketChannel socket : room.getAllUsers().keySet()) {
                this.send(socket, strSend);
            }
        }

        // 向服务端发送信息
        protected void sendAllUsersToRoom(Room room) {
            // 传递给客户端的房间在线用户
            String strOnline = "room online";

            // 生成传输消息
            String[] names = room.getAllNickNames();
            for (String name : names) {
                System.out.println("[info] username" + name);
                System.out.println("[info] roomname" + room.getRoomName());
                strOnline += "|" + name;
            }

            System.out.println("[info] 当前在线人数:" + room.getOnlineUserNum());

            //向房间内所有用户发送  发送房间内全部人员的名字
            sendToRoom(room, strOnline);
        }
    }

    // todo 关闭套接字，并将用户信息从在线列表中删除
    private String closeSocket() throws IOException {
//            String strUser = "";
//            for (int i = 0; i < socketUser.size(); i++) {//删除用户信息
//                if (curSocket.equals((Socket) socketUser.elementAt(i))) {
//                    strUser = onlineUser.elementAt(i).toString();
//                    socketUser.removeElementAt(i);
//                    onlineUser.removeElementAt(i);
//                    nameUser.removeElementAt(i);
//                    try {
//                        freshClientsOnline();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }

//            //删除房间信息
//            for (int i = 0; i < rooms.size(); i++) {
//                Room room = rooms.get(i);
//                if (room.getRoomNum().equals(RoomNum)){
//                    for (int j = 0; j < room.getCurUserNum(); j++) {
//                        if (socket.equals(room.findSocketUser(j))){
//                            room.removeOnlineUser(i);
//                            sendRoomUsers();
//                            sendAllRooms();
//                        }
//                    }
//                    rooms.remove(i);
//                    break;
//                }
//            }

//            try {
//                in.close();
//                out.close();
//                curSocket.close();
//            } catch (IOException e) {
//                System.out.println("[ERROR] " + e);
//            }
//            return strUser;

        return null;
    }
}
