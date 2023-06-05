package com.tankWar.game.entity;

/*
    Tank 坦克类
 */

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javafx.scene.image.Image;
import javafx.scene.shape.Rectangle;

import java.util.HashMap;

public class Tank extends Entity {
    // id 玩家编号
    private int id = -1;
    // 坦克是否可以移动
    private boolean isStop = true;
    // 记录坦克当前的子弹数量
    private int bulletNum = Config.TankMaxBulletNum;
    // 坦克速度
    private final int speed = Config.TankSpeed;


    // tank构造函数(随机方向)
    // 不能直接删除
    @JsonCreator
    public Tank(@JsonProperty("x") double x,
                @JsonProperty("y") double y,
                @JsonProperty("id") int id) {
        super(Config.TankWidth, Config.TankHeight, x, y);
        this.id = id;
        // 随机坦克方向
        double random = Math.random();
        if (random <= 0.25) {
            setDirection(Direction.UP);
        } else if (random <= 0.5) {
            setDirection(Direction.DOWN);
        } else if (random <= 0.75) {
            setDirection(Direction.LEFT);
        } else {
            setDirection(Direction.RIGHT);
        }
    }

    // tank构造函数(带方向)
    public Tank(double x, double y, Direction dir, int id) {
        super(Config.TankWidth, Config.TankHeight, x, y);
        this.id = id;
        switch (dir) {
            case UP -> setDirection(Direction.UP);
            case DOWN -> setDirection(Direction.DOWN);
            case LEFT -> setDirection(Direction.LEFT);
            case RIGHT -> setDirection(Direction.RIGHT);
            default -> System.out.println("Direction error");
        }
    }

    // 设置方向
    public void setDirection(Direction dir) {
        this.dir = dir;

        // 设置渲染图片方向的同时，重新设置长与宽
        // 图片由Map映射 不需要单独设置
        switch (dir) {
            case LEFT, RIGHT -> {
                this.width = Config.TankWidth;
                this.height = Config.TankHeight;
            }
            case UP, DOWN -> {
                this.width = Config.TankHeight;
                this.height = Config.TankWidth;
            }
            default -> System.out.println("Direction error");
        }
    }

    // 坦克移动函数
    public void move() {
        if(this.isStop)
            return;

        // 根据当前方向移动
        switch (dir) {
            case LEFT -> {
                if (x - this.width / 2 - speed >= 0) {
                    x = x - speed;
                }
            }
            case RIGHT -> {
                if (x + this.width / 2 + speed <= Config.MapWidth) {
                    x = x + speed;
                }
            }
            case UP -> {
                if (y - this.height / 2 - speed >= 0) {
                    y = y - speed;
                }
            }
            case DOWN -> {
                if (y + this.height / 2 + speed <= Config.MapHeight) {
                    y = y + speed;
                }
            }
            default -> System.out.println("Direction error");
        }
    }

    // 发射子弹
    public Bullet fire() {
        // 验证子弹是否发射
        if(this.bulletNum <= 0) {
            return null;
        }

        // 简化创建子弹的代码
        Bullet bullet = null;
        double x = this.x, y = this.y;
        // 枪口到子弹中心点位置
        double distance = + Config.TankHeight / 2 + Config.BulletSize / 2;
        switch (this.dir) {
            case UP ->  y = this.y - distance;
            case DOWN -> y = this.y + distance;
            case LEFT -> x = this.x - distance;
            case RIGHT -> x = this.x + distance;
            default -> System.out.println("Direction error");
        }

        bullet = new Bullet(this, this.dir, x, y);
        bulletNum -= 1;

        return bullet;
    }

    // 恢复最大子弹数量
    public void recoveryBullet() {
        if(bulletNum < Config.TankMaxBulletNum)
            this.bulletNum ++;
    }

    // 碰撞检测
    public boolean isCollidingWith(Entity entity) {
        if (entity.isAlive()) {
            Rectangle boundBox = null;
            // 将坦克尾部从碰撞域中减去
            float delta = (Config.TankWidth - Config.TankHeight) / 2;
            switch (this.dir) {
                case UP ->
                        boundBox = new Rectangle(this.x - this.width / 2, this.y - this.height / 2, this.width, this.height - delta);
                case DOWN ->
                        boundBox = new Rectangle(this.x - this.width / 2, this.y - this.height / 2 + delta, this.width, this.height - delta);
                case LEFT ->
                        boundBox = new Rectangle(this.x - this.width / 2, this.y - this.height / 2, this.width - delta, this.height);
                case RIGHT ->
                        boundBox = new Rectangle(this.x - this.width / 2 + delta, this.y - this.height / 2, this.width - delta, this.height);
            }
            assert boundBox != null;
            return boundBox.intersects(entity.x - entity.width / 2, entity.y - entity.height / 2, entity.width, entity.height);
        }
        return false;
    }

    @JsonIgnore
    @Override
    public Image getImage() {
        return TankImg.ImageMap.get(this.dir);
    }

    public int getId() {
        return id;
    }

    @JsonIgnore
    public boolean getIsStop() {
        return isStop;
    }

    public void setIsStop(boolean isStop) {
        this.isStop = isStop;
    }
}

// 使用其他类来记录图像 降低数据和资源的耦合度
class TankImg {
    // 坦克不同方向照片
    public static final HashMap<Direction, Image> ImageMap = new HashMap<Direction, Image>();

    static {
        try {
            ImageMap.put(Direction.UP, new Image("/image/tankUp.png"));
            ImageMap.put(Direction.DOWN, new Image("/image/tankDown.png"));
            ImageMap.put(Direction.LEFT, new Image("/image/tankLeft.png"));
            ImageMap.put(Direction.RIGHT, new Image("/image/tankRight.png"));
        }
        catch(Exception e) {
//            e.printStackTrace();
        }
    }
}