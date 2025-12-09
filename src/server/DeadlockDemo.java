package server;

import common.Train;

/**
 * 死锁演示类
 * 对应课件要求：演示死锁的发生
 * 场景：两个线程分别持有不同的资源(锁)，并试图获取对方持有的资源
 */
public class DeadlockDemo {

    // 定义两个资源对象 (两列火车)
    private static final Object lockG101 = new Object();
    private static final Object lockD202 = new Object();

    public static void main(String[] args) {
        System.out.println(">>> 💀 死锁演示程序启动...");
        System.out.println(">>> 场景：线程A持有G101锁想换D202，线程B持有D202锁想换G101");

        // 线程A：先锁 G101，再请求 D202
        Thread threadA = new Thread(() -> {
            try {
                synchronized (lockG101) {
                    System.out.println("线程[A] 已锁住 G101，正在处理业务...");
                    Thread.sleep(100); // 模拟业务处理耗时，确保线程B也能锁住它的资源

                    System.out.println("线程[A] 试图获取 D202 的锁...");
                    synchronized (lockD202) {
                        System.out.println("线程[A] 成功获取 D202，换票成功！");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        // 线程B：先锁 D202，再请求 G101 (注意加锁顺序与A相反，这是死锁的根源)
        Thread threadB = new Thread(() -> {
            try {
                synchronized (lockD202) {
                    System.out.println("线程[B] 已锁住 D202，正在处理业务...");
                    Thread.sleep(100);

                    System.out.println("线程[B] 试图获取 G101 的锁...");
                    synchronized (lockG101) {
                        System.out.println("线程[B] 成功获取 G101，换票成功！");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        threadA.setName("User-A");
        threadB.setName("User-B");

        threadA.start();
        threadB.start();

        // 检测逻辑：如果程序一直不结束，说明死锁了
    }
}