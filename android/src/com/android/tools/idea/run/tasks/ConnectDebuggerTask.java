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
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import org.jetbrains.annotations.NotNull;

/** A {@link ConnectDebuggerTask} is similar to a {@link LaunchTask}, except that running it creates a new launch descriptor and process. */
public interface ConnectDebuggerTask {
  @NotNull
  String getDescription();

  int getDuration();

  /**
   * Sets the timeout duration for the task.
   *
   * @param timeoutSeconds timeout duration in seconds for this connect debugger task to be aborted. 0 or negative number means
   *                       there is no timeout.
   */
  void setTimeoutSeconds(int timeoutSeconds);

  /**
   * Returns the timeout duration for the task.
   */
  int getTimeoutSeconds();

  void perform(@NotNull LaunchInfo launchInfo,
               @NotNull IDevice device,
               @NotNull ProcessHandlerLaunchStatus state,
               @NotNull ProcessHandlerConsolePrinter printer);
}
