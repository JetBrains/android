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

import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * Describes the state of launch (execution of {@link com.intellij.execution.configurations.RunConfiguration}).
 */
public interface LaunchStatus {
  /**
   * Returns true if all termination conditions registered by {@link #addLaunchTerminationCondition} are met, or when the launch is
   * Forcefully terminated by {@link #terminateLaunch}. Otherwise returns false.
   */
  boolean isLaunchTerminated();

  /**
   * Adds a launch termination condition to the status.
   *
   * @param launchTerminatedCondition a function which returns true when the launch should consider be terminated otherwise false.
   */
  void addLaunchTerminationCondition(BooleanSupplier launchTerminatedCondition);

  /**
   * Forcefully terminates this launch regardless of {@link #isLaunchTerminated()} value.
   *
   * @param errorMessage an optional error message to be shown to users to explain why the launch is terminated. Set null or
   *                     empty string if this termination is not an error.
   * @param destroyProcess if this is true, the underlying processes (if any) will be destroyed as well.
   */
  void terminateLaunch(@Nullable String errorMessage, boolean destroyProcess);
}
