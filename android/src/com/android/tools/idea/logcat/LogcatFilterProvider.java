/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.ddmlib.ClientData;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A hook for plugins that want to provide their own logcat filters.
 */
public interface LogcatFilterProvider {
  ExtensionPointName<LogcatFilterProvider> EP_NAME =
    ExtensionPointName.create("com.android.logcat.filterProvider");

  /**
   * Provide a filter that should act on Android logcat input. {@link ClientData} is passed in,
   * containing information about the active debuggable process, if any. In particular,
   * {@link ClientData#getPid()} can be useful for constructing an {@link AndroidLogcatFilter} with
   * narrowed scope.
   *
   * If {@code client} is {@code null}, it means that no client is focused (e.g. any filter you
   * provide should act on all logcat text).
   */
  @NotNull
  AndroidLogcatFilter getFilter(@Nullable ClientData client);
}
