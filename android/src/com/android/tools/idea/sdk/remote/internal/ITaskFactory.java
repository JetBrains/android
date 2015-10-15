/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.idea.sdk.remote.internal;


/**
 * A factory that can start and run new {@link ITask}s.
 */
public interface ITaskFactory {

  /**
   * Starts a new task with a new {@link com.android.tools.idea.sdk.remote.internal.ITaskMonitor}.
   * <p/>
   * The task will execute in a thread and runs it own UI loop.
   * This means the task can perform UI operations using
   * {@code Display#asyncExec(Runnable)}.
   * <p/>
   * In either case, the method only returns when the task has finished.
   *
   * @param title The title of the task, displayed in the monitor if any.
   * @param task  The task to run.
   */
  void start(String title, ITask task);

  /**
   * Starts a new task contributing to an already existing {@link com.android.tools.idea.sdk.remote.internal.ITaskMonitor}.
   * <p/>
   * To use this properly, you should use {@link com.android.tools.idea.sdk.remote.internal.ITaskMonitor#createSubMonitor(int)}
   * and give the sub-monitor to the new task with the number of work units you want
   * it to fill. The {@link #start} method will make sure to <em>fill</em> the progress
   * when the task is completed, in case the actual task did not.
   * <p/>
   * When a task is started from within a monitor, it reuses the thread
   * from the parent. Otherwise it starts a new thread and runs it own
   * UI loop. This means the task can perform UI operations using
   * {@code Display#asyncExec(Runnable)}.
   * <p/>
   * In either case, the method only returns when the task has finished.
   *
   * @param title         The title of the task, displayed in the monitor if any.
   * @param parentMonitor The parent monitor. Can be null.
   * @param task          The task to run and have it display on the monitor.
   */
  void start(String title, ITaskMonitor parentMonitor, ITask task);
}
