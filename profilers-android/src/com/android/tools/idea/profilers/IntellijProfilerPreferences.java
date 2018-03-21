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
package com.android.tools.idea.profilers;

import com.android.tools.profilers.ProfilerPreferences;
import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Simple wrapper for {@link PropertiesComponent#getInstance()} that caches key-value pairs across studio instances.
 * For per-studio session caching, use {@link TemporaryProfilerPreferences} instead.
 */
public class IntellijProfilerPreferences implements ProfilerPreferences {
  // prefix to be used for each key to avoid clashing in the global xml space.
  private static final String PROFILER_PREFIX = "studio.profiler";

  @NotNull private final PropertiesComponent myPropertiesComponent;

  public IntellijProfilerPreferences() {
    myPropertiesComponent = PropertiesComponent.getInstance();
  }

  @NotNull
  @Override
  public String getValue(@NotNull String name, @NotNull String defaultValue) {
    return myPropertiesComponent.getValue(PROFILER_PREFIX + name, defaultValue);
  }

  @Override
  public float getFloat(@NotNull String name, float defaultValue) {
    return myPropertiesComponent.getFloat(PROFILER_PREFIX + name, defaultValue);
  }

  @Override
  public int getInt(@NotNull String name, int defaultValue) {
    return myPropertiesComponent.getInt(PROFILER_PREFIX + name, defaultValue);
  }

  @Override
  public boolean getBoolean(@NotNull String name, boolean defaultValue) {
    return myPropertiesComponent.getBoolean(PROFILER_PREFIX + name, defaultValue);
  }

  @Override
  public void setValue(@NotNull String name, @NotNull String value) {
    myPropertiesComponent.setValue(PROFILER_PREFIX + name, value);
  }

  @Override
  public void setFloat(@NotNull String name, float value) {
    myPropertiesComponent.setValue(PROFILER_PREFIX + name, value, 0f);
  }

  @Override
  public void setInt(@NotNull String name, int value) {
    myPropertiesComponent.setValue(PROFILER_PREFIX + name, value, 0);
  }

  @Override
  public void setBoolean(@NotNull String name, boolean value) {
    myPropertiesComponent.setValue(PROFILER_PREFIX + name, value);
  }
}
