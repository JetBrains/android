/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import com.intellij.openapi.components.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent state management for configurations.
 * <p>
 * A configuration consists of some per-layout properties (such as orientation
 * and theme) and some project-wide properties (such as the locale or rendering target).
 * The {@linkplain ConfigurationStateManager} is responsible for papering over these
 * differences and providing persistence for configuration changes.
 */
public interface ConfigurationStateManager extends PersistentStateComponent<ConfigurationStateManager.State> {

  ConfigurationFileState getConfigurationState(@NotNull VirtualFile file);

  void setConfigurationState(@NotNull VirtualFile file, @NotNull ConfigurationFileState state);

  @NotNull
  ConfigurationProjectState getProjectState();

  /** Persisted state */
  class State {
    private ConfigurationProjectState myProjectState;

    @Tag("shared")
    @Property(surroundWithTag = false)
    public ConfigurationProjectState getProjectState() {
      return myProjectState;
    }

    public void setProjectState(ConfigurationProjectState projectState) {
      myProjectState = projectState;
    }

    private Map<String, ConfigurationFileState> myUrlToStateMap = new HashMap<>();

    @XMap(propertyElementName = "layouts", keyAttributeName = "url", entryTagName = "layout")
    public Map<String, ConfigurationFileState> getUrlToStateMap() {
      return myUrlToStateMap;
    }

    public void setUrlToStateMap(Map<String, ConfigurationFileState> urlToState) {
      myUrlToStateMap = urlToState;
    }
  }
}
