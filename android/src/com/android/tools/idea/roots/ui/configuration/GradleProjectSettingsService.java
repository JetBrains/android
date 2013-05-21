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
package com.android.tools.idea.roots.ui.configuration;

import com.android.tools.idea.actions.DisabledProjectStructureAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.IdeaProjectSettingsService;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Disables any calls to the "Project Structure" dialog for any project.
 */
public class GradleProjectSettingsService extends IdeaProjectSettingsService {
  public GradleProjectSettingsService(Project project) {
    super(project);
  }

  @Override
  public void openArtifactSettings(@Nullable Artifact artifact) {
    DisabledProjectStructureAction.showDisabledProjectStructureDialogMessage();
  }

  @Override
  public void openContentEntriesSettings(Module module) {
    DisabledProjectStructureAction.showDisabledProjectStructureDialogMessage();
  }

  @Override
  public void openLibrary(@NotNull Library library) {
    DisabledProjectStructureAction.showDisabledProjectStructureDialogMessage();
  }

  @Override
  public void openModuleDependenciesSettings(@NotNull Module module, @Nullable OrderEntry orderEntry) {
    DisabledProjectStructureAction.showDisabledProjectStructureDialogMessage();
  }

  @Override
  public void openModuleLibrarySettings(Module module) {
    DisabledProjectStructureAction.showDisabledProjectStructureDialogMessage();
  }

  @Override
  public void openModuleSettings(Module module) {
    DisabledProjectStructureAction.showDisabledProjectStructureDialogMessage();
  }

  @Override
  public void openLibraryOrSdkSettings(@NotNull OrderEntry orderEntry) {
    DisabledProjectStructureAction.showDisabledProjectStructureDialogMessage();
  }
}
