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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Studio-specific implementation of {@link ConfigurationStateManager}. */
@State(name = "AndroidLayouts", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class StudioConfigurationStateManager implements ConfigurationStateManager {
  private final Map<VirtualFile, ConfigurationFileState> myFileToState = new HashMap<>();
  private ConfigurationProjectState myProjectState = new ConfigurationProjectState();

  @NotNull
  public static ConfigurationStateManager get(@NotNull Project project) {
    return project.getService(ConfigurationStateManager.class);
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

  @NotNull
  public ConfigurationProjectState getProjectState() {
    return myProjectState;
  }

  @VisibleForTesting
  void setProjectState(@NotNull ConfigurationProjectState projectState) {
    myProjectState = projectState;
  }

  @Override
  public ConfigurationStateManager.State getState() {
    final Map<String, ConfigurationFileState> urlToState = new HashMap<>();

    synchronized (myFileToState) {
      for (Map.Entry<VirtualFile, ConfigurationFileState> entry : myFileToState.entrySet()) {
        urlToState.put(entry.getKey().getUrl(), entry.getValue());
      }
    }
    final ConfigurationStateManager.State state = new ConfigurationStateManager.State();
    state.setUrlToStateMap(urlToState);
    state.setProjectState(myProjectState);
    return state;
  }

  @Override
  public void loadState(@NotNull ConfigurationStateManager.State state) {
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
}
