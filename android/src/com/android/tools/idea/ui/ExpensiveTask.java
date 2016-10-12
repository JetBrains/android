/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A task which may take a while to run and which also may get interrupted. Therefore, it will be
 * run on a background thread, and its {@link #onFinished()} will only be called if it completed
 * without another task preempting it.
 *
 * <p/>This class is useful if you know you may repeatedly do an expensive operation, but you only
 * care about the result of the most recent one. For example, your UI may kick off an expensive
 * validation step each time the user changes some text in a text field.
 *
 * <p/>To start a task, use an {@link ExpensiveTask.Runner} instance. For example:
 *
 * <pre>
 *   private class ExpensiveCalculation extends ExpensiveTask { ... }
 *   ExpensiveTask.Runner myRunner = new ExpensiveTask.Runner();
 *
 *   void doExpensiveCalculation() {
 *     myRunner.setTask(new ExpensiveCalculation());
 *   }
 * </pre>
 */
public abstract class ExpensiveTask {
  /**
   * Called on the main thread. Useful for doing some UI operations before an expensive operation
   * begins.
   */
  public void onStarting() {}

  /**
   * Called on a background thread and should handle the long running operation. When complete,
   * and if not interrupted by another task, {@link #onFinished()} will be called on the main
   * thread.
   */
  public abstract void doBackgroundWork() throws Exception;

  /**
   * Called on the main thread. If this expensive task has been interrupted by another, this method
   * will not be called.
   */
  public abstract void onFinished();

  /**
   * A class responsible for running an {@link ExpensiveTask} on a background thread. This class only
   * runs a single task at a time, so that, if dozens of tasks are set one after the other, all the
   * intermediate tasks will be skipped.
   *
   * @see #setTask(ExpensiveTask)
   */
  public static final class Runner {

    @Nullable ExpensiveTaskWorker myActiveWorker;

    /**
     * Set a task, which should begin running as soon as possible. If a previous task is already
     * set, this one will be run as soon as that one finishes.
     */
    public void setTask(@NotNull ExpensiveTask task) {
      // If there's already a running task, it will be responsible for starting the next one
      boolean runImmediately = myActiveWorker == null;
      cancel();

      myActiveWorker = new ExpensiveTaskWorker(task);
      if (runImmediately) {
        myActiveWorker.onStarting();
        myActiveWorker.execute();
      }
    }

    /**
     * Cancel any current tasks that are running / enqueued to run.
     */
    public void cancel() {
      if (myActiveWorker != null) {
        myActiveWorker.cancel(true);
        myActiveWorker = null;
      }
    }

    private final class ExpensiveTaskWorker extends SwingWorker<Void, Void> {
      private final ExpensiveTask myTask;

      public ExpensiveTaskWorker(@NotNull ExpensiveTask task) {
        myTask = task;
      }

      /**
       * Unfortunately, SwingWorker doesn't have an override for when it starts. Therefore, we add
       * our own. We must call this right before calling {@link #execute()}, which is fragile, but at
       * least is isolated as an implementation detail.
       */
      public void onStarting() {
        myTask.onStarting();
      }

      @Override
      protected Void doInBackground() throws Exception {
        myTask.doBackgroundWork();
        return null;
      }

      @Override
      protected void done() {
        if (myActiveWorker == this) {
          myTask.onFinished();
          myActiveWorker = null;
        }
        else if (myActiveWorker != null) {
          // If we get here here, we were interrupted by a new worker
          myActiveWorker.onStarting();
          myActiveWorker.execute();
        }
      }
    }
  }
}
