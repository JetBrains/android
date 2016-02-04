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

import com.android.annotations.VisibleForTesting;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Persistent state management for configurations.
 * <p>
 * A configuration consists of some per-layout properties (such as orientation
 * and theme) and some project-wide properties (such as the locale or rendering target).
 * The {@linkplain ConfigurationStateManager} is responsible for papering over these
 * differences and providing persistence for configuration changes.
 */
@State(name = "AndroidLayouts", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ConfigurationStateManager implements PersistentStateComponent<ConfigurationStateManager.State> {
  private final Map<VirtualFile, ConfigurationFileState> myFileToState = new HashMap<VirtualFile, ConfigurationFileState>();
  private ConfigurationProjectState myProjectState = new ConfigurationProjectState();

  @NotNull
  public static ConfigurationStateManager get(@NotNull Project project) {
    return ServiceManager.getService(project, ConfigurationStateManager.class);
  }

  @Nullable
  public ConfigurationFileState getConfigurationState(@NotNull VirtualFile file) {
    synchronized (myFileToState) {
      return myFileToState.get(file);
    }
  }

  public void setConfigurationState(@NotNull VirtualFile file, @NotNull ConfigurationFileState state) {
    synchronized (myFileToState) {
      myFileToState.put(file, state);
    }
  }

  public void removeConfigurationState(@NotNull VirtualFile file) {
    synchronized (myFileToState) {
      myFileToState.remove(file);
    }
  }

  @NotNull
  public ConfigurationProjectState getProjectState() {
    return myProjectState;
  }

  @VisibleForTesting
  void setProjectState(@NotNull ConfigurationProjectState projectState) {
    myProjectState = projectState;
  }

  @Override
  public State getState() {
    final Map<String, ConfigurationFileState> urlToState = new HashMap<String, ConfigurationFileState>();

    synchronized (myFileToState) {
      for (Map.Entry<VirtualFile, ConfigurationFileState> entry : myFileToState.entrySet()) {
        urlToState.put(entry.getKey().getUrl(), entry.getValue());
      }
    }
    final State state = new State();
    state.setUrlToStateMap(urlToState);
    state.setProjectState(myProjectState);
    return state;
  }

  @Override
  public void loadState(State state) {
    myProjectState = state.getProjectState();

    synchronized (myFileToState) {
      myFileToState.clear();

      for (Map.Entry<String, ConfigurationFileState> entry : state.getUrlToStateMap().entrySet()) {
        final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(entry.getKey());

        if (file != null) {
          myFileToState.put(file, entry.getValue());
        }
      }
    }
  }

  /** Persisted state */
  public static class State {
    private ConfigurationProjectState myProjectState;

    @Tag("shared")
    @Property(surroundWithTag = false)
    public ConfigurationProjectState getProjectState() {
      return myProjectState;
    }

    public void setProjectState(ConfigurationProjectState projectState) {
      myProjectState = projectState;
    }

    private Map<String, ConfigurationFileState> myUrlToStateMap = new HashMap<String, ConfigurationFileState>();

    @Tag("layouts")
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundWithTag = false, surroundValueWithTag = false, surroundKeyWithTag = false,
                   keyAttributeName = "url", entryTagName = "layout")
    public Map<String, ConfigurationFileState> getUrlToStateMap() {
      return myUrlToStateMap;
    }

    public void setUrlToStateMap(Map<String, ConfigurationFileState> urlToState) {
      myUrlToStateMap = urlToState;
    }
  }
}
