/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers;

import org.jetbrains.annotations.NotNull;

/**
 * A collection of APIs used for caching and retrieving arbitrary key-value pairs (e.g. settings).
 */
public interface ProfilerPreferences {
  @NotNull
  String getValue(@NotNull String name, @NotNull String defaultValue);

  float getFloat(@NotNull String name, float defaultValue);

  int getInt(@NotNull String name, int defaultValue);

  boolean getBoolean(@NotNull String name, boolean defaultValue);

  void setValue(@NotNull String name, @NotNull String value);

  void setFloat(@NotNull String name, float value);

  void setInt(@NotNull String name, int value);

  void setBoolean(@NotNull String name, boolean value);
}
