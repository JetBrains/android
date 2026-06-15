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
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Studio-specific implementation of {@link ConfigurationStateManager}. */
@State(name = "AndroidLayouts", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class StudioConfigurationStateManager implements ConfigurationStateManager {
  private final Project myProject;
  private final Map<VirtualFile, ConfigurationFileState> myFileToState = new HashMap<>();
  // States loaded from workspace.xml that have not been resolved to VirtualFiles yet.
  // We keep them unresolved to avoid calling VirtualFileManager.findFileByUrl during loadState,
  // which can trigger network/disk hits (and leak NTLM hashes on Windows) before project trust is verified.
  // It also avoids race conditions with VFS startup refresh where files might not be cached yet.
  private final Map<String, ConfigurationFileState> myUnresolvedStates = new HashMap<>();
  private ConfigurationProjectState myProjectState = new ConfigurationProjectState();

  public StudioConfigurationStateManager(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public static ConfigurationStateManager get(@NotNull Project project) {
    return project.getService(ConfigurationStateManager.class);
  }

  @Nullable
  public ConfigurationFileState getConfigurationState(@NotNull VirtualFile file) {
    // Security gate: Do not expose configuration state in Safe Mode.
    if (!TrustedProjects.isProjectTrusted(myProject)) {
      return null;
    }
    synchronized (myFileToState) {
      ConfigurationFileState state = myFileToState.get(file);
      if (state == null) {
        // Lazy resolve: If not in cache, resolve from unresolved states using the file's URL.
        // This is safe because we already have the VirtualFile, so we don't need to resolve the URL string
        // via VirtualFileManager which could trigger network/disk hits.
        state = myUnresolvedStates.remove(file.getUrl());
        if (state != null) {
          myFileToState.put(file, state);
        }
      }
      return state;
    }
  }

  public void setConfigurationState(@NotNull VirtualFile file, @NotNull ConfigurationFileState state) {
    synchronized (myFileToState) {
      myFileToState.put(file, state);
      // Make sure we remove it from unresolved states if it was overwritten.
      myUnresolvedStates.remove(file.getUrl());
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
      // Preserve unresolved states so they are not lost when saving workspace.xml
      // if they were never accessed (resolved) in this session.
      urlToState.putAll(myUnresolvedStates);
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
      myUnresolvedStates.clear();
      // Store all states as unresolved to avoid resolving URLs at startup.
      // Resolving them early is unsafe (potential UNC network hits in Safe Mode)
      // and unreliable (files might not be in VFS cache yet during startup refresh).
      myUnresolvedStates.putAll(state.getUrlToStateMap());
    }
  }
}
