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
import org.jetbrains.annotations.Nullable;

/**
 * Simple wrapper for {@link PropertiesComponent#getInstance()}.
 * Note that as described in IntelliJ's docoumentation, the PropertiesComponent exists at global scope and care should be taken in
 * choosing the name for the key-value pair for each settings. (e.g. prepend with profiler's namespace)
 */
public class IntellijProfilerPreferences implements ProfilerPreferences {

  @NotNull private final PropertiesComponent myPropertiesComponent;

  public IntellijProfilerPreferences() {
    myPropertiesComponent = PropertiesComponent.getInstance();
  }

  @NotNull
  @Override
  public String getValue(@NotNull String name, @NotNull String defaultValue) {
    return myPropertiesComponent.getValue(name, defaultValue);
  }

  @Override
  public float getFloat(@NotNull String name, float defaultValue) {
    return myPropertiesComponent.getFloat(name, defaultValue);
  }

  @Override
  public int getInt(@NotNull String name, int defaultValue) {
    return myPropertiesComponent.getInt(name, defaultValue);
  }

  @Override
  public boolean getBoolean(@NotNull String name, boolean defaultValue) {
    return myPropertiesComponent.getBoolean(name, defaultValue);
  }

  @Override
  public void setValue(@NotNull String name, @NotNull String value) {
    myPropertiesComponent.setValue(name, value);
  }

  @Override
  public void setFloat(@NotNull String name, float value) {
    myPropertiesComponent.setValue(name, value, 0f);
  }

  @Override
  public void setInt(@NotNull String name, int value) {
    myPropertiesComponent.setValue(name, value, 0);
  }

  @Override
  public void setBoolean(@NotNull String name, boolean value) {
    myPropertiesComponent.setValue(name, value);
  }
}
