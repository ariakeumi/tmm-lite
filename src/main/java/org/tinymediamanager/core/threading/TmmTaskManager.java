/*
 * Copyright 2012 - 2026 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.core.threading;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmTaskHandle.TaskState;
import org.tinymediamanager.core.threading.TmmThreadPool.TmmThreadFactory;

/**
 * The class TmmTaskManager. Used to manage all tasks within tmm (except the helper tasks, e.g. scraper sub tasks)
 * 
 * @author Manuel Laggner
 */
public class TmmTaskManager implements TmmTaskListener {
  private static final Logger            LOGGER        = LoggerFactory.getLogger(TmmTaskManager.class);

  public final AtomicLong                GLOB_THRD_CNT = new AtomicLong(1);

  private static final TmmTaskManager    instance      = new TmmTaskManager();
  private final Set<TmmTaskListener>     taskListener  = new CopyOnWriteArraySet<>();
  private final Set<TmmTaskHandle>       runningTasks  = new CopyOnWriteArraySet<>();

  // we have some "named" queues, holding different types of tasks
  // image download/subtitle download are rather small/fast tasks - we only queue them in a queue and provide to abort the complete queue
  private ThreadPoolExecutor             imageDownloadExecutor;

  private ThreadPoolExecutor             imageCacheExecutor;

  // this is a queue which holds "other" tasks
  private ThreadPoolExecutor             unnamedTaskExecutor;

  // trailer download are rather big/long-running tasks; only x at a time can be run and they are able to be cancelled individually
  private ThreadPoolExecutor             downloadExecutor;

  // main tasks (update datasource, scraping, renaming) are queueable tasks, but only one at a time can run; they can be cancelled individually
  private final ThreadPoolExecutor       mainTaskExecutor;

  // fake task handles to manage queues
  private final TmmTaskHandle            imageDownloadHandle;
  private final TmmTaskHandle            imageCacheHandle;

  // scheduled threads
  private final ScheduledExecutorService scheduler     = Executors.newScheduledThreadPool(1);

  private boolean                        isShutdown    = false;

  private TmmTaskManager() {
    mainTaskExecutor = createMainTaskQueue();

    imageDownloadHandle = new ImageDownloadTaskHandle();
    imageCacheHandle = new ImageCacheTaskHandle();

    Settings.getInstance().addPropertyChangeListener("maximumDownloadThreads", e -> {
      // only need to set this if there is already an executor. otherwise the executor will be created with the right amount
      if (downloadExecutor != null) {
        // we have to make sure the maximum pool size is always larger than the core pool size
        if (downloadExecutor.getMaximumPoolSize() < Settings.getInstance().getMaximumDownloadThreads()) {
          downloadExecutor.setMaximumPoolSize(Settings.getInstance().getMaximumDownloadThreads());
          downloadExecutor.setCorePoolSize(Settings.getInstance().getMaximumDownloadThreads());
        }
        else {
          downloadExecutor.setCorePoolSize(Settings.getInstance().getMaximumDownloadThreads());
          downloadExecutor.setMaximumPoolSize(Settings.getInstance().getMaximumDownloadThreads());
        }
        downloadExecutor.prestartAllCoreThreads(); // force new threads to be started if we've increased the thread count
      }
    });
  }

  public static TmmTaskManager getInstance() {
    return instance;
  }

  public void addTaskListener(TmmTaskListener listener) {
    taskListener.add(listener);
  }

  public void removeTaskListener(TmmTaskListener listener) {
    taskListener.remove(listener);
  }

  private ThreadPoolExecutor createImageDownloadExecutor() {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(3, 3, 2, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
        new TmmThreadFactory("image-download-task")) {
      @Override
      protected void beforeExecute(Thread d, Runnable r) {
        super.beforeExecute(d, r);
        if (imageDownloadHandle != null) {
          processTaskEvent(imageDownloadHandle);
        }
      }

      @Override
      protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        // afterExecute() is still called while the worker holds its lock, so getActiveCount() would
        // still count the just finished task as running -> notify listeners a bit later to deliver the real state
        scheduler.schedule(() -> {
          if (imageDownloadHandle != null) {
            processTaskEvent(imageDownloadHandle);
          }
        }, 250, TimeUnit.MILLISECONDS);
      }
    };
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  private ThreadPoolExecutor createImageCacheExecutor() {
    int threadCount = Runtime.getRuntime().availableProcessors() / 2;
    if (threadCount < 2) {
      threadCount = 2;
    }
    ThreadPoolExecutor executor = new ThreadPoolExecutor(threadCount, threadCount, 2, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
        new TmmThreadFactory("image-cache-task")) {
      @Override
      protected void beforeExecute(Thread d, Runnable r) {
        super.beforeExecute(d, r);
        if (imageCacheHandle != null) {
          processTaskEvent(imageCacheHandle);
        }
      }

      @Override
      protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        // afterExecute() is still called while the worker holds its lock, so getActiveCount() would
        // still count the just finished task as running -> notify listeners a bit later to deliver the real state
        scheduler.schedule(() -> {
          if (imageCacheHandle != null) {
            processTaskEvent(imageCacheHandle);
          }
        }, 250, TimeUnit.MILLISECONDS);
      }
    };
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  private ThreadPoolExecutor createUnnamedTaskExecutor() {
    // create enough thread to keep the system busy ;)
    int threadCount = Runtime.getRuntime().availableProcessors() / 2;
    if (threadCount < 2) {
      threadCount = 2;
    }
    ThreadPoolExecutor executor = new ThreadPoolExecutor(threadCount, threadCount, 2, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
        new TmmThreadFactory("unnamed-task"));
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  /**
   * add an image download task to the queue
   * 
   * @param task
   *          the task to be added
   */
  public void addImageDownloadTask(Runnable task) {
    if (isShutdown) {
      return;
    }

    if (imageDownloadExecutor == null || imageDownloadExecutor.isShutdown()) {
      imageDownloadExecutor = createImageDownloadExecutor();
    }
    imageDownloadExecutor.execute(task);
  }

  /**
   * add an image cache task to the queue
   *
   * @param task
   *          the task to be added
   */
  public void addImageCacheTask(Runnable task) {
    if (isShutdown) {
      return;
    }

    if (imageCacheExecutor == null || imageCacheExecutor.isShutdown()) {
      imageCacheExecutor = createImageCacheExecutor();
    }
    imageCacheExecutor.execute(task);
  }

  /**
   * add a tasks which does not fit in the named queues (like caching or TV show episode scraping task)
   * 
   * @param task
   *          the task to be added
   */
  public void addUnnamedTask(TmmTask task) {
    if (isShutdown) {
      return;
    }

    if (unnamedTaskExecutor == null || unnamedTaskExecutor.isShutdown()) {
      unnamedTaskExecutor = createUnnamedTaskExecutor();
    }
    task.setState(TaskState.QUEUED);
    task.addListener(this);
    // immediately inform this listener
    processTaskEvent(task);
    unnamedTaskExecutor.execute(task);
  }

  /**
   * add a tasks which does not fit in the named queues (like caching or TV show episode scraping task)
   *
   * @param task
   *          the task to be added
   */
  public void addUnnamedTask(Runnable task) {
    if (isShutdown) {
      return;
    }

    if (unnamedTaskExecutor == null || unnamedTaskExecutor.isShutdown()) {
      unnamedTaskExecutor = createUnnamedTaskExecutor();
    }
    unnamedTaskExecutor.execute(task);
  }

  /**
   * add a download task to the queue
   * 
   * @param task
   *          the task to be added
   */
  public void addDownloadTask(TmmTask task) {
    if (isShutdown) {
      return;
    }

    if (downloadExecutor == null) {
      downloadExecutor = new ThreadPoolExecutor(Settings.getInstance().getMaximumDownloadThreads(),
          Settings.getInstance().getMaximumDownloadThreads(), 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
          new TmmThreadFactory("download-task"));
      downloadExecutor.allowCoreThreadTimeOut(true);
    }
    task.setState(TaskState.QUEUED);
    task.addListener(this);
    // immediately inform this listener
    processTaskEvent(task);
    downloadExecutor.execute(task);
  }

  /**
   * cancel all open and running image downloads
   */
  public void cancelImageDownloads() {
    if (imageDownloadExecutor != null) {
      imageDownloadExecutor.shutdownNow();
    }
  }

  /**
   * cancel all open and running image cache tasks
   */
  public void cancelImageCache() {
    if (imageCacheExecutor != null) {
      imageCacheExecutor.shutdownNow();
    }
  }

  /**
   * add a new task to the main task queue
   *
   * @param newTask
   *          the task to be added
   */
  public void addMainTask(TmmThreadPool newTask) {
    if (isShutdown) {
      return;
    }

    newTask.setState(TaskState.QUEUED);
    newTask.addListener(this);
    // immediately inform this listener
    processTaskEvent(newTask);
    mainTaskExecutor.execute(newTask);
  }

  private ThreadPoolExecutor createMainTaskQueue() {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, // max threads
        1, TimeUnit.SECONDS, // time to wait before closing idle workers
        new LinkedBlockingQueue<>(), // our queue
        new TmmThreadFactory("main-task"));
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  /**
   * shut down all threads
   */
  public void shutdown() {
    LOGGER.info("Sending shutdown signal to running tasks");
    isShutdown = true;

    if (imageDownloadExecutor != null) {
      imageDownloadExecutor.shutdown();
    }
    if (imageCacheExecutor != null) {
      imageCacheExecutor.shutdown();
    }
    if (unnamedTaskExecutor != null) {
      unnamedTaskExecutor.shutdown();
    }
    if (downloadExecutor != null) {
      downloadExecutor.shutdown();
    }

    mainTaskExecutor.shutdown();
    scheduler.shutdown();

    for (TmmTaskHandle task : runningTasks) {
      task.cancel();
    }
  }

  /**
   * hard shutdown of all tasks after a max of 4 secs waiting
   */
  public void shutdownNow() {
    LOGGER.info("Stopping task executors");
    isShutdown = true;

    // check if all finished
    boolean stopped = true;

    try {
      if (imageDownloadExecutor != null && !imageDownloadExecutor.isTerminated()) {
        imageDownloadExecutor.shutdownNow();
        stopped = stopped && imageDownloadExecutor.awaitTermination(5, TimeUnit.SECONDS);
      }
      if (imageCacheExecutor != null && !imageCacheExecutor.isTerminated()) {
        imageCacheExecutor.shutdownNow();
        stopped = stopped && imageCacheExecutor.awaitTermination(5, TimeUnit.SECONDS);
      }
      if (unnamedTaskExecutor != null && !unnamedTaskExecutor.isTerminated()) {
        unnamedTaskExecutor.shutdownNow();
        stopped = stopped && unnamedTaskExecutor.awaitTermination(5, TimeUnit.SECONDS);
      }
      if (downloadExecutor != null && !downloadExecutor.isTerminated()) {
        downloadExecutor.shutdownNow();
        stopped = stopped && downloadExecutor.awaitTermination(5, TimeUnit.SECONDS);
      }
      if (!mainTaskExecutor.isTerminated()) {
        mainTaskExecutor.shutdownNow();
        stopped = stopped && mainTaskExecutor.awaitTermination(5, TimeUnit.SECONDS);
      }
      if (!scheduler.isTerminated()) {
        scheduler.shutdownNow();
        stopped = stopped && scheduler.awaitTermination(5, TimeUnit.SECONDS);
      }
    }
    catch (InterruptedException ignored) {
      // ignore
    }
    if (stopped) {
      LOGGER.info("All task executors could be stopped");
    }
    else {
      LOGGER.warn("Could not stop all task executors");
    }
  }

  /**
   * is a TMM thread pool running?!
   */
  public boolean isPoolRunning() {
    return checkForThreadAlive("tmmpool");
  }

  /**
   * check if there is any main task running
   *
   * @return true/false
   */
  public boolean isMainTaskRunning() {
    return mainTaskExecutor != null && (mainTaskExecutor.getActiveCount() > 0 || !mainTaskExecutor.getQueue().isEmpty());
  }

  /**
   * check if there are any image downloads running
   * 
   * @return true/false
   */
  public boolean isImageDownloadsRunning() {
    return imageDownloadExecutor != null && (imageDownloadExecutor.getActiveCount() > 0 || !imageDownloadExecutor.getQueue().isEmpty());
  }

  /**
   * Look for a text in name of running threads to check if some threads have not shut down yet
   * 
   * @param contains
   *          the String to look for in thread name
   * @return true if a running thread's name contains given String
   */
  private boolean checkForThreadAlive(String contains) {
    // ignore local thread
    Thread local = Thread.currentThread();
    for (Thread t : getAllThreads()) {
      if (t != local && t.isAlive() && getThreadName(t).contains(contains)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get all threads of our own threadgroup (threads started by our webapplication).
   * 
   * @return all threads
   */
  private Thread[] getAllThreads() {
    ThreadGroup root = Thread.currentThread().getThreadGroup();

    int nAlloc = root.activeCount();
    int n = 0;
    Thread[] threads;
    do {
      nAlloc *= 2;
      threads = new Thread[nAlloc];
      n = root.enumerate(threads, true);
    } while (n == nAlloc);

    return java.util.Arrays.copyOf(threads, n);
  }

  /**
   * Get thread's name in lowercase.
   * 
   * @param t
   *          the thread
   * @return the thread name
   */
  private String getThreadName(Thread t) {
    return (t != null && !isEmpty(t.getName())) ? t.getName().toLowerCase(Locale.ROOT) : "";
  }

  private static boolean isEmpty(CharSequence cs) {
    return cs == null || cs.isEmpty();
  }

  @Override
  public void processTaskEvent(TmmTaskHandle task) {
    if (task.getState() == TaskState.STARTED) {
      runningTasks.add(task);
    }
    if (task.getState() == TaskState.FINISHED || task.getState() == TaskState.FAILED) {
      runningTasks.remove(task);
    }
    for (TmmTaskListener listener : taskListener) {
      listener.processTaskEvent(task);
    }
  }

  /*************************************************************************
   * helper classes
   *************************************************************************/
  private class ImageDownloadTaskHandle implements TmmTaskHandle {
    @Override
    public String getTaskName() {
      return TmmResourceBundle.getString("task.imagedownloads");
    }

    @Override
    public int getWorkUnits() {
      int unit = 0;
      if (imageDownloadExecutor != null) {
        unit = (int) imageDownloadExecutor.getTaskCount();
      }
      return unit;
    }

    @Override
    public int getProgressDone() {
      int done = 0;
      if (imageDownloadExecutor != null) {
        done = (int) imageDownloadExecutor.getCompletedTaskCount();
      }
      return done;
    }

    @Override
    public String getTaskDescription() {
      return getOpenTasks() + " " + TmmResourceBundle.getString("task.remaining");
    }

    private int getOpenTasks() {
      int openTasks = 0;
      if (imageDownloadExecutor != null) {
        openTasks = imageDownloadExecutor.getQueue().size() + imageDownloadExecutor.getActiveCount();
      }
      return openTasks;
    }

    @Override
    public TaskState getState() {
      if (imageDownloadHandle != null && getOpenTasks() > 0) {
        return TaskState.STARTED;
      }
      return TaskState.FINISHED;
    }

    @Override
    public TaskType getType() {
      return TaskType.BACKGROUND_TASK;
    }

    @Override
    public void cancel() {
      cancelImageDownloads();
      processTaskEvent(imageDownloadHandle);
    }

    @Override
    public String toString() {
      return getType().name() + " image " + getState().name() + " " + getProgressDone() + "/" + getWorkUnits();
    }
  }

  private class ImageCacheTaskHandle implements TmmTaskHandle {
    @Override
    public String getTaskName() {
      return TmmResourceBundle.getString("tmm.rebuildimagecache");
    }

    @Override
    public int getWorkUnits() {
      int unit = 0;
      if (imageCacheExecutor != null) {
        unit = (int) imageCacheExecutor.getTaskCount();
      }
      return unit;
    }

    @Override
    public int getProgressDone() {
      int done = 0;
      if (imageCacheExecutor != null) {
        done = (int) imageCacheExecutor.getCompletedTaskCount();
      }
      return done;
    }

    @Override
    public String getTaskDescription() {
      return getOpenTasks() + " " + TmmResourceBundle.getString("task.remaining");
    }

    private int getOpenTasks() {
      int openTasks = 0;
      if (imageCacheExecutor != null) {
        openTasks = imageCacheExecutor.getQueue().size() + imageCacheExecutor.getActiveCount();
      }
      return openTasks;
    }

    @Override
    public TaskState getState() {
      if (imageCacheHandle != null && getOpenTasks() > 0) {
        return TaskState.STARTED;
      }
      return TaskState.FINISHED;
    }

    @Override
    public TaskType getType() {
      return TaskType.BACKGROUND_TASK;
    }

    @Override
    public void cancel() {
      cancelImageCache();
      processTaskEvent(imageCacheHandle);
    }

    @Override
    public String toString() {
      return getType().name() + " image " + getState().name() + " " + getProgressDone() + "/" + getWorkUnits();
    }
  }
}
