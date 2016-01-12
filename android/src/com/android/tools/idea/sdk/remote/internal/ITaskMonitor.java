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

import com.android.utils.ILogger;


/**
 * A monitor interface for a {@link ITask}.
 * <p/>
 * Depending on the task factory that created the task, there might not be any UI
 * or it might not implement all the methods, in which case calling them would be
 * a no-op but is guaranteed not to crash.
 * <p/>
 * If the task runs in a non-UI worker thread, the task factory implementation
 * will take care of the update the UI in the correct thread. The task itself
 * must not have to deal with it.
 * <p/>
 * A monitor typically has 3 levels of text displayed: <br/>
 * - A <b>title</b> <em>may</em> be present on a task dialog, typically when a task
 * dialog is created. This is not covered by this monitor interface. <br/>
 * - A <b>description</b> displays prominent information on what the task
 * is currently doing. This is expected to vary over time, typically changing
 * with each sub-monitor, and typically only the last description is visible.
 * For example an updater would typically have descriptions such as "downloading",
 * "installing" and finally "done". This is set using {@link #setDescription}. <br/>
 * - A <b>verbose</b> optional log that can provide more information than the summary
 * description and is typically displayed in some kind of scrollable multi-line
 * text field so that the user can keep track of what happened. 3 levels are
 * provided: error, normal and verbose. An UI may hide the log till an error is
 * logged and/or might hide the verbose text unless a flag is checked by the user.
 * This is set using {@link #log}, {@link #logError} and {@link #logVerbose}.
 * <p/>
 * A monitor is also an {@link ILogger} implementation.
 */
public interface ITaskMonitor extends ILogger {

  /**
   * Sets the description in the current task dialog.
   * This method can be invoked from a non-UI thread.
   */
  void setDescription(String format, Object... args);

  /**
   * Logs a "normal" information line.
   * This method can be invoked from a non-UI thread.
   */
  void log(String format, Object... args);

  /**
   * Logs an "error" information line.
   * This method can be invoked from a non-UI thread.
   */
  void logError(String format, Object... args);

  /**
   * Logs a "verbose" information line, that is extra details which are typically
   * not that useful for the end-user and might be hidden until explicitly shown.
   * This method can be invoked from a non-UI thread.
   */
  void logVerbose(String format, Object... args);

  /**
   * Sets the max value of the progress bar.
   * This method can be invoked from a non-UI thread.
   * <p/>
   * This method MUST be invoked once before using {@link #incProgress(int)} or
   * {@link #getProgress()} or {@link #createSubMonitor(int)}. Callers are
   * discouraged from using more than once -- implementations can either discard
   * the next calls or behave incoherently.
   */
  void setProgressMax(int max);

  /**
   * Returns the max value of the progress bar, as last set by {@link #setProgressMax(int)}.
   * Returns 0 if the max has never been set yet.
   */
  int getProgressMax();

  /**
   * Increments the current value of the progress bar.
   * This method can be invoked from a non-UI thread.
   * <p/>
   * Callers MUST use setProgressMax before using this method.
   */
  void incProgress(int delta);

  /**
   * Returns the current value of the progress bar,
   * between 0 and up to {@link #setProgressMax(int)} - 1.
   * <p/>
   * Callers MUST use setProgressMax before using this method.
   */
  int getProgress();

  /**
   * Creates a sub-monitor that will use up to tickCount on the progress bar.
   * tickCount must be 1 or more.
   */
  ITaskMonitor createSubMonitor(int tickCount);
}
