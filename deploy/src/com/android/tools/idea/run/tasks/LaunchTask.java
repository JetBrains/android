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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.wireless.android.sdk.stats.LaunchTaskDetail;
import com.intellij.execution.Executor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * A step in launching a run configuration on device, for example installing an APK or unlocking
 * the screen.
 *
 * <p>You can define your launch task by two ways. An easy and recommended way is to implement
 * {@link #perform}. This method is invoked by {@link org.jetbrains.ide.PooledThreadExecutor} so you
 * can do expensive operation here directly. If you need more control, this is not recommended
 * however, you can override {@link #run} and do whatever you want. It is your responsibility to
 * call {@link #perform} from your customized {@link #run} method.
 */
public interface LaunchTask {
  /**
   * A description which may get shown to the user as the task is being launched.
   * <p>
   * The description should start with a verb using present continuous tense for the verb,
   * e.g. "Launching X", "Opening Y", "Starting Z"
   */
  @NotNull
  String getDescription();

  /**
   * Returns an estimated duration of this launch task. The unit of the duration is unit-less
   * value defined in {@link LaunchTaskDurations}. This value is only used to compute a completion
   * ratio of a given set of tasks.
   */
  int getDuration();

  /**
   * Runs this LaunchTask. This method is an entry point of this launch task and is called by
   * {@link org.jetbrains.ide.PooledThreadExecutor} so you can perform expensive operations here.
   *
   * @param executor a metadata of the executor of this task. Note that this is not a
   *                 {@code java.util.concurrent.Executor}
   * @param device an Android device to perform this task against
   * @param launchStatus a current status of this launch operation. An implementor of this method
   *                     should check the status periodically and cancel ongoing operations if it is
   *                     being terminated.
   * @param printer use this printer to output arbitrary messages
   * @return the result of this task
   */
  default LaunchResult run(@NotNull Executor executor, @NotNull IDevice device,
                           @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    boolean success = perform(device, launchStatus, printer);
    return success ? LaunchResult.success() : LaunchResult.error("", getDescription());
  }

  /**
   * Performs this task. Do not call this method directly, call {@link #run} instead. This method is
   * public for historical reason but this should have been protected method.
   *
   * <p>This method is called by {@link org.jetbrains.ide.PooledThreadExecutor} so you can perform
   * expensive operations here.
   *
   * @param device an android device to perform this task against
   * @param launchStatus a current status of this launch operation. An implementor of this method
   *                     should check the status periodically and cancel ongoing operations if it is
   *                     being terminated.
   * @param printer use this printer to output arbitrary messages
   * @return true on success otherwise false
   */
  boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus,
                  @NotNull ConsolePrinter printer);

  /**
   * Returns an arbitrary identifier string for this task. This ID is recorded in
   * {@link com.android.tools.idea.stats.RunStats} and included in the user stats data. So be sure
   * that your ID does not conflict with others. Typically, your launch task class name is used in
   * capital letters. e.g. "MY_LAUNCH_TASK".
   */
  @NotNull
  String getId();

  /**
   * Returns a collection of APK information which is used or installed by this task. This method
   * is used solely for collecting stats data.
   *
   * <p>Note that this method is not implemented yet as of April 2019.
   */
  @NotNull
  default Collection<ApkInfo> getApkInfos() {
    return Collections.emptyList();
  }

  /**
   * Returns a collection of additional stats for this task. This method is used solely for
   * collecting stats data.
   */
  @NotNull
  default Collection<LaunchTaskDetail> getSubTaskDetails() {
    return Collections.emptyList();
  }
}
