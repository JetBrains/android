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
package com.android.tools.idea.flags;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.flags.Flag;
import com.android.flags.FlagOverrides;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Serialized settings for {@link StudioFlags}. Meant only for production code, not unit tests.
 */
@State(
  name = "StudioFlags",
  storages = @Storage("studioFlags.xml"),
  reloadable = false
)
public final class StudioFlagSettings implements FlagOverrides, PersistentStateComponent<StudioFlagSettings> {
  public Map<String, String> data = new HashMap<>();

  /**
   * Note: This method will throw an exception if called from unit tests.
   */
  @NotNull
  public static StudioFlagSettings getInstance() {
    return ServiceManager.getService(StudioFlagSettings.class);
  }

  @Override
  public StudioFlagSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull StudioFlagSettings settings) {
    XmlSerializerUtil.copyBean(settings, this);
  }

  @Override
  public void clear() {
    data.clear();
  }

  @Override
  public void put(@NonNull Flag<?> flag, @NonNull String value) {
    data.put(flag.getId(), value);
  }

  @Override
  public void remove(@NonNull Flag<?> flag) {
    data.remove(flag.getId());
  }

  @Nullable
  @Override
  public String get(@NonNull Flag<?> flag) {
    return data.get(flag.getId());
  }
}
