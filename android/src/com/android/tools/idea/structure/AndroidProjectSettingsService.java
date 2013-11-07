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
package com.android.tools.idea.structure;

import com.android.tools.idea.gradle.util.Projects;
import com.intellij.compiler.actions.ArtifactAwareProjectSettingsService;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.projectWizard.JdkChooserPanel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.*;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This subclass of {@linkplain ProjectSettingsService} disables navigation to Project Settings panes that don't apply to
 * Gradle-based projects. For non-Gradle projects, it forwards calls to a delegate instance that preserves normal functionality.
 */
public class AndroidProjectSettingsService extends ProjectSettingsService implements ArtifactAwareProjectSettingsService {
  private final Project myProject;
  private final IdeaProjectSettingsService myDelegate;

  public AndroidProjectSettingsService(final Project project) {
    myProject = project;
    myDelegate = new IdeaProjectSettingsService(project);
  }

  @Override
  public void openProjectSettings() {
    myDelegate.openProjectSettings();
  }

  @Override
  public void openGlobalLibraries() {
    if (Projects.isGradleProject(myProject)) {
      openProjectSettings();
    } else {
      myDelegate.openGlobalLibraries();
    }
  }

  @Override
  public void openLibrary(@NotNull final Library library) {
    if (Projects.isGradleProject(myProject)) {
      openProjectSettings();
    } else {
      myDelegate.openLibrary(library);
    }
  }

  @Override
  public boolean canOpenModuleSettings() {
    if (Projects.isGradleProject(myProject)) {
      return true;
    } else {
      return myDelegate.canOpenModuleSettings();
    }
  }

  @Override
  public void openModuleSettings(final Module module) {
    if (Projects.isGradleProject(myProject)) {
      AndroidModuleStructureConfigurable.showDialog(myProject, module.getName(), null);
    } else {
      myDelegate.openModuleSettings(module);
    }
  }

  @Override
  public boolean canOpenModuleLibrarySettings() {
    if (Projects.isGradleProject(myProject)) {
      return false;
    } else {
      return myDelegate.canOpenModuleLibrarySettings();
    }
  }

  @Override
  public void openModuleLibrarySettings(final Module module) {
    if (Projects.isGradleProject(myProject)) {
      openModuleSettings(module);
    } else {
      myDelegate.openModuleLibrarySettings(module);
    }
  }

  @Override
  public boolean canOpenContentEntriesSettings() {
    if (Projects.isGradleProject(myProject)) {
      return false;
    } else {
      return myDelegate.canOpenContentEntriesSettings();
    }
  }

  @Override
  public void openContentEntriesSettings(final Module module) {
    if (Projects.isGradleProject(myProject)) {
      openModuleSettings(module);
    } else {
      myDelegate.openContentEntriesSettings(module);
    }
  }

  @Override
  public boolean canOpenModuleDependenciesSettings() {
    if (Projects.isGradleProject(myProject)) {
      // TODO: This is something we ought to be able to do. However, it's not clear that there's any code path that can reach this method.
      return false;
    } else {
      return myDelegate.canOpenModuleDependenciesSettings();
    }
  }

  @Override
  public void openModuleDependenciesSettings(@NotNull final Module module, @Nullable final OrderEntry orderEntry) {
    if (Projects.isGradleProject(myProject)) {
      openModuleSettings(module);
    } else {
      myDelegate.openModuleDependenciesSettings(module, orderEntry);
    }
  }

  @Override
  public boolean canOpenLibraryOrSdkSettings(OrderEntry orderEntry) {
    if (Projects.isGradleProject(myProject)) {
      return false;
    } else {
      return myDelegate.canOpenLibraryOrSdkSettings(orderEntry);
    }
  }

  @Override
  public void openLibraryOrSdkSettings(@NotNull final OrderEntry orderEntry) {
    if (Projects.isGradleProject(myProject)) {
      openProjectSettings();
    } else {
      myDelegate.openLibraryOrSdkSettings(orderEntry);
    }
  }

  @Override
  public boolean processModulesMoved(final Module[] modules, @Nullable final ModuleGroup targetGroup) {
    if (Projects.isGradleProject(myProject)) {
      return false;
    } else {
      return myDelegate.processModulesMoved(modules, targetGroup);
    }
  }

  @Override
  public void showModuleConfigurationDialog(String moduleToSelect, String editorNameToSelect) {
    if (Projects.isGradleProject(myProject)) {
      AndroidModuleStructureConfigurable.showDialog(myProject, moduleToSelect, editorNameToSelect);
    } else {
      myDelegate.showModuleConfigurationDialog(moduleToSelect, editorNameToSelect);
    }
  }

  @Override
  public Sdk chooseAndSetSdk() {
    // TODO: We may not want to always call the delegate here. I'm not sure of what the right thing is.
    return myDelegate.chooseAndSetSdk();
  }

  @Override
  public void openArtifactSettings(@Nullable Artifact artifact) {
    if (Projects.isGradleProject(myProject)) {
      openProjectSettings();
    } else {
      myDelegate.openArtifactSettings(artifact);
    }
  }
}
