# 大厅部分说明

## 登陆/注册逻辑

1. 用户启动客户端
2. 客户端使用配置文件中的连接信息与服务端建立TCP连接，在连接过程中，用户可以选择退出客户端。
3. 若能够建立连接，则用户可以正常使用登陆和注册的功能
4. 若不能建立连接，则提示用户重新设置配置文件。此时用户可以选择重新连接，否则退出客户端。

## 窗口退出逻辑
下面将说明窗口退出对服务端逻辑的影响，主要考虑两种退出模式

* 主动退出：用户发出明确的退出指令，一般是窗口退出。
* 异常退出：用户退出时没有发出退出指示，需要通过`try/catch`捕获。一般是网络异常。

尽量保证窗口的退出前都保证发出明确指令（通过确认/取消弹窗），同时对发生异常的情况进行捕获，保证程序的鲁棒性。

### 登陆窗口
* 用户没有与服务端建立连接，那么会提示正在连接，且如果不连接则会直接退出连接。

* 用户已经建立连接，服务端则会开始维护`User`对象集合，需要考虑将用户从User的表中删除，但实际上对全局影响不是很大。

### 大厅窗口

用户处于大厅窗口与处于登陆窗口相似。但值得注意的是，用户在登陆成功后，会出现在在线列表中，服务端需要重新维护在线玩家列表。

玩家退出后保留玩家信息5分钟，如果玩家断开连接5分钟后，还没有进入则会删除该玩家的信息。如果玩家正在游戏中，则会持久化保留该玩家的信息，直至游戏结束。

* users
* 在线玩家列表

### 房间窗口

用户处于房间窗口时，说明已经进入某个房间，玩家如果关闭窗口，首先需要退出房间，即维护房间的在线列表，之后与大厅窗口相同。

当房主退出的时候需要转移房主。

* room
* users
* 在线玩家列表

### 游戏窗口

游戏窗口实时性比较强，存在两个重要的状态信息，所有玩家信息和剩余玩家信息。玩家退出消息则会<u>回到房间界面</u>， 并不会直接断开连接。
当玩家退出游戏后，他的信息将会保留，但很显然他的坦克就会再动了，直至玩家重新进入该房间。对于玩家断连的情况，我们也这么处理。玩家可以通过重新连接访问，但暂时我们不考虑重连的问题。

对于客户端，其他玩家也需要了解到有玩家退出游戏了。

~~如果玩家存活，那么退出则标记其死亡。如果已死亡则不需要额外处理。同时，我们还需要修改所有玩家信息。但为了维护游戏的整体性，我们保留积分信息，也就是所有玩家信息保留，但在每轮游戏开始时，就标记其死亡。~~

~~当房间内只剩余一名玩家时，直接对其判胜即可。只考虑突出的玩家不是房主，如果房主退出，需要考虑房间转让的问题。~~

* 游戏内状态（所有玩家信息，剩余玩家信息）
* room
* users
* 在线玩家列表

