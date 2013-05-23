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

import com.android.tools.idea.gradle.util.Projects;
import com.intellij.ide.actions.ShowStructureSettingsAction;
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
  private final boolean myIsGradleProject;

  public GradleProjectSettingsService(Project project) {
    super(project);
    myIsGradleProject = Projects.isGradleProject(project);
  }

  @Override
  public void openArtifactSettings(@Nullable Artifact artifact) {
    if (myIsGradleProject) {
      ShowStructureSettingsAction.showDisabledProjectStructureDialogMessage();
      return;
    }
    super.openArtifactSettings(artifact);
  }

  @Override
  public void openContentEntriesSettings(Module module) {
    if (myIsGradleProject) {
      ShowStructureSettingsAction.showDisabledProjectStructureDialogMessage();
      return;
    }
    super.openContentEntriesSettings(module);
  }

  @Override
  public void openLibrary(@NotNull Library library) {
    if (myIsGradleProject) {
      ShowStructureSettingsAction.showDisabledProjectStructureDialogMessage();
      return;
    }
    super.openLibrary(library);
  }

  @Override
  public void openModuleDependenciesSettings(@NotNull Module module, @Nullable OrderEntry orderEntry) {
    if (myIsGradleProject) {
      ShowStructureSettingsAction.showDisabledProjectStructureDialogMessage();
      return;
    }
    super.openModuleDependenciesSettings(module, orderEntry);
  }

  @Override
  public void openModuleLibrarySettings(Module module) {
    if (myIsGradleProject) {
      ShowStructureSettingsAction.showDisabledProjectStructureDialogMessage();
      return;
    }
    super.openModuleLibrarySettings(module);
  }

  @Override
  public void openModuleSettings(Module module) {
    if (myIsGradleProject) {
      ShowStructureSettingsAction.showDisabledProjectStructureDialogMessage();
      return;
    }
    super.openModuleSettings(module);
  }

  @Override
  public void openLibraryOrSdkSettings(@NotNull OrderEntry orderEntry) {
    if (myIsGradleProject) {
      ShowStructureSettingsAction.showDisabledProjectStructureDialogMessage();
      return;
    }
    super.openLibraryOrSdkSettings(orderEntry);
  }
}
