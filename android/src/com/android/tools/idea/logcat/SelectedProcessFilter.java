/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.logcat;

import com.android.ddmlib.Log.LogLevel;
import org.jetbrains.annotations.NotNull;

final class SelectedProcessFilter implements AndroidLogcatFilter {
  private final int myProcessId;

  SelectedProcessFilter(int processId) {
    myProcessId = processId;
  }

  @NotNull
  @Override
  public String getName() {
    return AndroidLogcatView.SELECTED_APP_FILTER;
  }

  /**
   * @param p the process
   */
  @Override
  public boolean isApplicable(@NotNull String message, @NotNull String tag, @NotNull String p, int processId, @NotNull LogLevel priority) {
    return myProcessId == processId;
  }
}
