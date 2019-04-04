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

public interface LaunchTask {
  /**
   * A description which may get shown to the user as the task is being launched.
   * <p>
   * The description should start with a verb using present continuous tense for the verb,
   * e.g. "Launching X", "Opening Y", "Starting Z"
   */
  @NotNull
  String getDescription();

  int getDuration();

  boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer);

  default LaunchResult run(@NotNull Executor executor, @NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    boolean success = perform(device, launchStatus, printer);
    LaunchResult result = new LaunchResult();
    result.setSuccess(success);
    if (!success) {
      result.setError("Error " + getDescription());
      result.setErrorId("");
      result.setConsoleError("Error while " + getDescription());
    }
    return result;
  }

  @NotNull
  String getId();

  @NotNull
  default Collection<ApkInfo> getApkInfos() {
    return Collections.emptyList();
  }

  @NotNull
  default Collection<LaunchTaskDetail> getSubTaskDetails() {
    return Collections.emptyList();
  }
}
