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
package com.android.tools.idea.logcat;

import com.android.ddmlib.logcat.LogCatMessage;
import org.jetbrains.annotations.NotNull;

/**
 * A filter which can reject lines of logcat output.
 */
public interface AndroidLogcatFilter {
  /**
   * Returns the name of this filter, necessary so the UI can present the user with a list of named
   * filters to choose form.
   */
  @NotNull
  String getName();

  /**
   * Returns {@code true} if the current logcat message should be accepted, {@code false} otherwise.
   */
  boolean isApplicable(@NotNull LogCatMessage logCatMessage);
}
