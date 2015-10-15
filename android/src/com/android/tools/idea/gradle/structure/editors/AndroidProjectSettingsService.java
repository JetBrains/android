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
package com.android.tools.idea.gradle.structure.editors;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.startup.AndroidStudioInitializer;
import com.android.tools.idea.gradle.structure.AndroidProjectStructureConfigurable;
import com.intellij.compiler.actions.ArtifactAwareProjectSettingsService;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.IdeaProjectSettingsService;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
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

  public AndroidProjectSettingsService(Project project) {
    myProject = project;
    myDelegate = new IdeaProjectSettingsService(project);
  }

  @Override
  public void openProjectSettings() {
    myDelegate.openProjectSettings();
  }

  @Override
  public void openGlobalLibraries() {
    if (!isGradleProjectInAndroidStudio()) {
      myDelegate.openGlobalLibraries();
    }
  }

  @Override
  public void openLibrary(@NotNull Library library) {
    if (!isGradleProjectInAndroidStudio()) {
      myDelegate.openLibrary(library);
    }
  }

  @Override
  public boolean canOpenModuleSettings() {
    if (isGradleProjectInAndroidStudio()) {
      return true;
    }
    else {
      return myDelegate.canOpenModuleSettings();
    }
  }

  @Override
  public void openModuleLibrarySettings(Module module) {
    if (isGradleProjectInAndroidStudio()) {
      openModuleSettings(module);
    }
    else {
      myDelegate.openModuleLibrarySettings(module);
    }
  }

  @Override
  public void openModuleSettings(Module module) {
    if (isGradleProjectInAndroidStudio()) {
      AndroidProjectStructureConfigurable.getInstance(myProject).showDialogAndSelect(module);
    }
    else {
      myDelegate.openModuleSettings(module);
    }
  }

  public void openSigningConfiguration(@NotNull Module module) {
    AndroidProjectStructureConfigurable configurable = AndroidProjectStructureConfigurable.getInstance(myProject);
    configurable.showDialogAndOpenSigningConfiguration(module);
  }

  public void openSdkSettings() {
    AndroidProjectStructureConfigurable configurable = AndroidProjectStructureConfigurable.getInstance(myProject);
    configurable.showDialogAndSelectSdksPage();
  }

  public void chooseJdkLocation() {
    AndroidProjectStructureConfigurable configurable = AndroidProjectStructureConfigurable.getInstance(myProject);
    configurable.showDialogAndChooseJdkLocation();
  }

  public void openAndSelectDependency(@NotNull Module module, @NotNull GradleCoordinate dependency) {
    AndroidProjectStructureConfigurable configurable = AndroidProjectStructureConfigurable.getInstance(myProject);
    configurable.showDialogAndSelectDependency(module, dependency);
  }

  public void openAndSelectBuildTypesEditor(@NotNull Module module) {
    AndroidProjectStructureConfigurable configurable = AndroidProjectStructureConfigurable.getInstance(myProject);
    configurable.showDialogAndSelectBuildTypesEditor(module);
  }

  public void openAndSelectFlavorsEditor(@NotNull Module module) {
    AndroidProjectStructureConfigurable configurable = AndroidProjectStructureConfigurable.getInstance(myProject);
    configurable.showDialogAndSelectFlavorsEditor(module);
  }

  public void openAndSelectDependenciesEditor(@NotNull Module module) {
    AndroidProjectStructureConfigurable configurable = AndroidProjectStructureConfigurable.getInstance(myProject);
    configurable.showDialogAndSelectDependenciesEditor(module);
  }

  @Override
  public boolean canOpenModuleLibrarySettings() {
    if (isGradleProjectInAndroidStudio()) {
      return false;
    }
    else {
      return myDelegate.canOpenModuleLibrarySettings();
    }
  }

  @Override
  public boolean canOpenContentEntriesSettings() {
    if (isGradleProjectInAndroidStudio()) {
      return false;
    }
    else {
      return myDelegate.canOpenContentEntriesSettings();
    }
  }

  @Override
  public void openContentEntriesSettings(Module module) {
    if (isGradleProjectInAndroidStudio()) {
      openModuleSettings(module);
    }
    else {
      myDelegate.openContentEntriesSettings(module);
    }
  }

  @Override
  public boolean canOpenModuleDependenciesSettings() {
    if (isGradleProjectInAndroidStudio()) {
      // TODO: This is something we ought to be able to do. However, it's not clear that there's any code path that can reach this method.
      return false;
    }
    else {
      return myDelegate.canOpenModuleDependenciesSettings();
    }
  }

  @Override
  public void openModuleDependenciesSettings(@NotNull Module module, @Nullable OrderEntry orderEntry) {
    if (isGradleProjectInAndroidStudio()) {
      openModuleSettings(module);
    }
    else {
      myDelegate.openModuleDependenciesSettings(module, orderEntry);
    }
  }

  @Override
  public boolean canOpenLibraryOrSdkSettings(OrderEntry orderEntry) {
    if (isGradleProjectInAndroidStudio()) {
      return false;
    }
    else {
      return myDelegate.canOpenLibraryOrSdkSettings(orderEntry);
    }
  }

  @Override
  public void openLibraryOrSdkSettings(@NotNull OrderEntry orderEntry) {
    if (!isGradleProjectInAndroidStudio()) {
      myDelegate.openLibraryOrSdkSettings(orderEntry);
    }
  }

  @Override
  public boolean processModulesMoved(Module[] modules, @Nullable ModuleGroup targetGroup) {
    if (isGradleProjectInAndroidStudio()) {
      return false;
    }
    else {
      return myDelegate.processModulesMoved(modules, targetGroup);
    }
  }

  @Override
  public void showModuleConfigurationDialog(String moduleToSelect, String editorNameToSelect) {
    if (isGradleProjectInAndroidStudio()) {
      Module module = ModuleManager.getInstance(myProject).findModuleByName(moduleToSelect);
      assert module != null;
      AndroidProjectStructureConfigurable.getInstance(myProject).showDialogAndSelect(module);
    }
    else {
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
    if (!isGradleProjectInAndroidStudio()) {
      myDelegate.openArtifactSettings(artifact);
    }
  }

  private boolean isGradleProjectInAndroidStudio() {
    return AndroidStudioInitializer.isAndroidStudio() && Projects.requiresAndroidModel(myProject);
  }
}
