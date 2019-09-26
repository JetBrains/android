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
package com.android.tools.idea.run.util;

import org.jetbrains.annotations.Nullable;

/**
 * Describes the state of launch (execution of {@link com.intellij.execution.configurations.RunConfiguration}).
 */
public interface LaunchStatus {
  /**
   * Returns true if the master process of this launch has been terminated.
   */
  boolean isLaunchTerminated();

  /**
   * Forcefully terminates this launch regardless of {@link #isLaunchTerminated()} value.
   *
   * This method is used when the launch has to be stopped by unforeseen reasons such as cancellation by a user.
   *
   * @param reason an optional message to be shown to users to explain why the launch is terminated.
   * @param destroyProcess if this is true, the underlying processes (if any) will be destroyed as well.
   */
  void terminateLaunch(@Nullable String reason, boolean destroyProcess);
}
