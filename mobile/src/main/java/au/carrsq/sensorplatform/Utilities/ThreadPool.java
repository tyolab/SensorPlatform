package au.carrsq.sensorplatform.Utilities;

import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPool {
    private static ThreadPool mInstance;
    private ThreadPoolExecutor mThreadPoolExec;
    private static int MAX_POOL_SIZE;
    private static final int KEEP_ALIVE = 20;
    BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();

    public static void post(Runnable runnable) {
        if (mInstance == null) {
            mInstance = new ThreadPool();
        }
        Log.d("THREADPOOL", "Added runnable: " + runnable.toString());
        mInstance.mThreadPoolExec.execute(runnable);

    }

    private ThreadPool() {
        int coreNum = Runtime.getRuntime().availableProcessors();
        MAX_POOL_SIZE = coreNum * 4;
        Log.d("THREADPOOL", "New pool: " + MAX_POOL_SIZE);
        mThreadPoolExec = new ThreadPoolExecutor(
                coreNum,
                MAX_POOL_SIZE,
                KEEP_ALIVE,
                TimeUnit.SECONDS,
                workQueue,
                new RejectedExecutionHandler() {
                    @Override
                    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor threadPoolExecutor) {
                        Log.d("THREADPOOL", runnable.toString() + " was rejected");
                    }
                });
    }

    public static void finish() {
        if(mInstance != null) {
            mInstance.mThreadPoolExec.shutdown();
            mInstance = null;
        }
        Log.d("THREADPOOL", "Stopped Pool");
    }
}