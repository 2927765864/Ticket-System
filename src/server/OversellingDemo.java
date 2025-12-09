package server;

/**
 * 超售现象演示 (错误案例)
 * 对应课件要求：演示会引起超售、座位冲突的场景
 * 原理：去掉 synchronized 锁，并在关键操作间加入延时，人为制造线程安全问题
 */
public class OversellingDemo {

    // 模拟只有 1 张余票
    private static int ticket = 1;

    public static void main(String[] args) {
        System.out.println(">>> 💥 超售演示程序启动 (模拟抢最后1张票)...");
        System.out.println(">>> 当前余票: " + ticket);

        // 模拟两个终端同时抢票
        Thread t1 = new Thread(new Buyer("终端A"));
        Thread t2 = new Thread(new Buyer("终端B"));

        t1.start();
        t2.start();
    }

    // 购票线程任务
    static class Buyer implements Runnable {
        private String name;

        public Buyer(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            // ❌ 错误示范：这里没有加 synchronized 锁
            // 步骤1：检查余票
            if (ticket > 0) {
                try {
                    // 关键点：故意在这里睡 100毫秒
                    // 模拟：终端A查到有票，正在扣款，还没来得及减库存，终端B也查到了有票
                    System.out.println(name + " 查到有票，正在出票中...");
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // 步骤2：扣减库存
                ticket--;
                System.out.println("✅ " + name + " 购票成功！当前余票: " + ticket);
            } else {
                System.out.println("❌ " + name + " 购票失败，没票了。");
            }
        }
    }
}