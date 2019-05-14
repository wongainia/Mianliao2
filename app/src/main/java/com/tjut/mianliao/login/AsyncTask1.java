//package com.tjut.mianliao.login;
//
//import android.os.AsyncTask;
//import android.os.Binder;
//import android.os.Handler;
//import android.os.Message;
//import android.os.Process;
//
//import java.util.ArrayDeque;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.Callable;
//import java.util.concurrent.CancellationException;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.Executor;
//import java.util.concurrent.FutureTask;
//import java.util.concurrent.LinkedBlockingQueue;
//import java.util.concurrent.ThreadFactory;
//import java.util.concurrent.ThreadPoolExecutor;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.TimeoutException;
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.concurrent.atomic.AtomicInteger;
//
///**
// * Created by dengming on 2018/1/19.
// */
//
//public abstract class AsyncTask1<Params,Progress,Result> {
//
//    private static final String LOG_TAG = "AsyncTask";
//    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
//    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
//    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
//    private static final int KEEP_ALIVE_SECONDS = 30;
//
//    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
//        private final AtomicInteger mCount = new AtomicInteger(1);
//
//        public Thread newThread(Runnable r) {
//            return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());
//        }
//    };
//
//    private static final BlockingQueue<Runnable> sPoolWorkQueue =
//            new LinkedBlockingQueue<Runnable>(128);
//
//    public static final Executor THREAD_POOL_EXECUTOR;
//
//    static {
//        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
//                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
//                sPoolWorkQueue, sThreadFactory);
//        threadPoolExecutor.allowCoreThreadTimeOut(true);
//        THREAD_POOL_EXECUTOR = threadPoolExecutor;
//    }
//
//    public static final Executor SERIAL_EXECUTOR = new SerialExecutor();
//
//    private static final int MESSAGE_POST_RESULT = 0x1;
//    private static final int MESSAGE_POST_PROGRESS = 0x2;
//
//    private static volatile Executor sDefaultExecutor = SERIAL_EXECUTOR;
//    private final WorkerRunnable<Params, Result> mWorker;
//    private final FutureTask<Result> mFuture;
//
//    private volatile AsyncTask.Status mStatus = AsyncTask.Status.PENDING;
//
//    private final AtomicBoolean mCancelled = new AtomicBoolean();
//    private final AtomicBoolean mTaskInvoked = new AtomicBoolean();
//
//    private static class SerialExecutor implements Executor {
//        final ArrayDeque<Runnable> mTasks = new ArrayDeque<Runnable>();
//        Runnable mActive;
//        public synchronized void execute(final Runnable r) {
//            mTasks.offer(new Runnable() {
//                public void run() {
//                    try {
//                        r.run();
//                    } finally {
//                        scheduleNext();
//                    }
//                }
//            });
//            if (mActive == null) {
//                scheduleNext();
//            }
//        }
//        protected synchronized void scheduleNext() {
//            if ((mActive = mTasks.poll()) != null) {
//                THREAD_POOL_EXECUTOR.execute(mActive);
//            }
//        }
//    }
//
//    public enum Status {
//        /**
//         * Indicates that the task has not been executed yet.
//         */
//        PENDING,
//        /**
//         * Indicates that the task is running.
//         */
//        RUNNING,
//        /**
//         * Indicates that {@link AsyncTask#onPostExecute} has finished.
//         */
//        FINISHED,
//    }
//
//    private static Handler getHandler() {
//        synchronized (AsyncTask.class) {
//            if (sHandler == null) {
//                sHandler = new InternalHandler();
//            }
//            return sHandler;
//        }
//    }
//
//    public static void setDefaultExecutor(Executor exec) {
//        sDefaultExecutor = exec;
//    }
//
//    public AsyncTask() {
//        mWorker = new WorkerRunnable<Params, Result>() {
//            public Result call() throws Exception {
//                mTaskInvoked.set(true);
//                Result result = null;
//                try {
//                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
//                    //noinspection unchecked
//                    result = doInBackground(mParams);
//                    Binder.flushPendingCommands();
//                } catch (Throwable tr) {
//                    mCancelled.set(true);
//                    throw tr;
//                } finally {
//                    postResult(result);
//                }
//                return result;
//            }
//        };
//
//        mFuture = new FutureTask<Result>(mWorker) {
//            @Override
//            protected void done() {
//                try {
//                    postResultIfNotInvoked(get());
//                } catch (InterruptedException e) {
//                    android.util.Log.w(LOG_TAG, e);
//                } catch (ExecutionException e) {
//                    throw new RuntimeException("An error occurred while executing doInBackground()",
//                            e.getCause());
//                } catch (CancellationException e) {
//                    postResultIfNotInvoked(null);
//                }
//            }
//        };
//    }
//
//    private void postResultIfNotInvoked(Result result) {
//        final boolean wasTaskInvoked = mTaskInvoked.get();
//        if (!wasTaskInvoked) {
//            postResult(result);
//        }
//    }
//
//    private Result postResult(Result result) {
//        @SuppressWarnings("unchecked")
//        Message message = getHandler().obtainMessage(MESSAGE_POST_RESULT,
//                new AsyncTaskResult<Result>(this, result));
//        message.sendToTarget();
//        return result;
//    }
//
//    public final AsyncTask.Status getStatus() {
//        return mStatus;
//    }
//
//    protected abstract Result doInBackground(Params... params);
//
//    protected void onPreExecute() {
//    }
//
//    protected void onPostExecute(Result result) {
//    }
//
//    protected void onProgressUpdate(Progress... values) {
//    }
//
//    protected void onCancelled(Result result) {
//        onCancelled();
//    }
//
//    protected void onCancelled() {
//    }
//
//    public final boolean isCancelled() {
//        return mCancelled.get();
//    }
//
//    public final boolean cancel(boolean mayInterruptIfRunning) {
//        mCancelled.set(true);
//        return mFuture.cancel(mayInterruptIfRunning);
//    }
//
//    public final Result get() throws InterruptedException, ExecutionException {
//        return mFuture.get();
//    }
//
//    public final Result get(long timeout, TimeUnit unit) throws InterruptedException,
//            ExecutionException, TimeoutException {
//        return mFuture.get(timeout, unit);
//    }
//
//    public static void execute(Runnable runnable) {
//        sDefaultExecutor.execute(runnable);
//    }
//
//    protected final void publishProgress(Progress... values) {
//        if (!isCancelled()) {
//            getHandler().obtainMessage(MESSAGE_POST_PROGRESS,
//                    new AsyncTaskResult<Progress>(this, values)).sendToTarget();
//        }
//    }
//
//    private void finish(Result result) {
//        if (isCancelled()) {
//            onCancelled(result);
//        } else {
//            onPostExecute(result);
//        }
//        mStatus = AsyncTask.Status.FINISHED;
//    }
//
//    private static abstract class WorkerRunnable<Params, Result> implements Callable<Result> {
//        Params[] mParams;
//    }
//
//    @SuppressWarnings({"RawUseOfParameterizedType"})
//    private static class AsyncTaskResult<Data> {
//        final AsyncTask mTask;
//        final Data[] mData;
//
//        AsyncTaskResult(AsyncTask task, Data... data) {
//            mTask = task;
//            mData = data;
//        }
//    }
//}
