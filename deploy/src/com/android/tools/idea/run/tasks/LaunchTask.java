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

import com.android.tools.idea.run.ApkInfo;
import com.google.wireless.android.sdk.stats.LaunchTaskDetail;
import com.intellij.execution.ExecutionException;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;

/**
 * A step in launching a run configuration on device, for example installing an APK or unlocking
 * the screen.
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
   * Checks whether or not this LaunchTask should run.
   *
   * @param launchContext additional parameters shared amongst all {@link LaunchTask}
   * @return true if {@link #run(LaunchContext)} should execute, false otherwise
   */
  default boolean shouldRun(@NotNull LaunchContext launchContext) {
    return true;
  }

  /**
   * Runs this LaunchTask. This method is an entry point of this launch task and is called by
   * {@link org.jetbrains.ide.PooledThreadExecutor} so you can perform expensive operations here.
   *
   * @param launchContext additional parameters shared amongst all {@link LaunchTask}s
   */
  void run(@NotNull LaunchContext launchContext) throws ExecutionException;

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
