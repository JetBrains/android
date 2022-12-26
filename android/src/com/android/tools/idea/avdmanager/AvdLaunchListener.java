/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.internal.avd.AvdInfo;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Listener of AVD launch events.
 */
public interface AvdLaunchListener {
  Topic<AvdLaunchListener> TOPIC = new Topic<>("AVD launch events", AvdLaunchListener.class);

  /**
   * Called when an AVD is launched from Studio.
   *
   * @param avd the AVD that was launched
   * @param commandLine the command line used to start the emulator
   * @param requestType the type of user action that triggered the launch
   * @param project the project on behalf of which the AVD was launched, or null if not applicable
   */
  void avdLaunched(
      @NotNull AvdInfo avd, @NotNull GeneralCommandLine commandLine, @NotNull RequestType requestType, @Nullable Project project);

  enum RequestType {
    /** AVD started by a direct user action in Device Manager. */
    DIRECT,
    /** AVD started as a side effect of some other action, e.g., launching an app or a test. */
    INDIRECT,
  }
}