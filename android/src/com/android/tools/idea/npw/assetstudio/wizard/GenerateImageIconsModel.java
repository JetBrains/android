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
package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * The model used by the Image Asset wizard.
 */
public final class GenerateImageIconsModel extends GenerateIconsModel {
  @NotNull private final StateStorage myStateStorage;

  public GenerateImageIconsModel(@NotNull AndroidFacet androidFacet) {
    super(androidFacet);
    Project project = androidFacet.getModule().getProject();
    myStateStorage = ServiceManager.getService(project, StateStorage.class);
    assert myStateStorage != null;
  }

  @Override
  protected void generateIntoPath(@NotNull AndroidModuleTemplate paths, @NotNull IconGenerator iconGenerator) {
    iconGenerator.generateImageIconsIntoPath(paths);
  }

  /**
   * Returns the persistent state associated with the wizard.
   */
  @NotNull
  public PersistentState getPersistentState() {
    return myStateStorage.getState();
  }

  @State(name = "ImageAssetSettings", storages = @Storage(file = "imageAssetSettings.xml"))
  public static class StateStorage implements PersistentStateComponent<PersistentState> {
    private PersistentState myState;

    @Override
    @NotNull
    public PersistentState getState() {
      if (myState == null) {
        myState = new PersistentState();
      }
      return myState;
    }

    @Override
    public void loadState(@NotNull PersistentState state) {
      myState = state;
    }

    @NotNull
    public static StateStorage getInstance(@NotNull Project project) {
      return ServiceManager.getService(project, StateStorage.class);
    }
  }
}
