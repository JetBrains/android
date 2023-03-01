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
package com.android.tools.idea.gradle.structure;

import static com.android.tools.idea.gradle.project.sync.hyperlink.OpenGradleSettingsHyperlink.showGradleSettings;
import static com.android.tools.idea.structure.dialog.ProjectStructureConfigurableKt.canShowPsd;
import static com.android.tools.idea.structure.dialog.ProjectStructureConfigurableKt.canShowPsdOrWarnUser;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getGradleIdentityPathOrNull;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.structure.configurables.BasePerspectiveConfigurableKt;
import com.android.tools.idea.gradle.structure.configurables.BuildVariantsPerspectiveConfigurableKt;
import com.android.tools.idea.gradle.structure.configurables.DependenciesPerspectiveConfigurableKt;
import com.android.tools.idea.gradle.structure.configurables.ModulesPerspectiveConfigurableKt;
import com.android.tools.idea.gradle.structure.configurables.ui.buildvariants.BuildVariantsPanelKt;
import com.android.tools.idea.gradle.structure.configurables.ui.buildvariants.buildtypes.BuildTypesPanelKt;
import com.android.tools.idea.gradle.structure.configurables.ui.buildvariants.productflavors.ProductFlavorsPanelKt;
import com.android.tools.idea.gradle.structure.configurables.ui.modules.ModulePanelKt;
import com.android.tools.idea.gradle.structure.configurables.ui.modules.SigningConfigsPanelKt;
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable;
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
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This subclass of {@linkplain ProjectSettingsService} disables navigation to Project Settings panes that don't apply to
 * Gradle-based projects. For non-Gradle projects, it forwards calls to a delegate instance that preserves normal functionality.
 */
public class AndroidProjectSettingsServiceImpl extends ProjectSettingsService implements AndroidProjectSettingsService,
                                                                                         ArtifactAwareProjectSettingsService {
  private final Project myProject;
  private final IdeaProjectSettingsService myDelegate;

  public AndroidProjectSettingsServiceImpl(Project project) {
    myProject = project;
    myDelegate = new IdeaProjectSettingsService(project);
  }

  @Override
  public void openProjectSettings() {
    myDelegate.openProjectSettings();
  }

  @Override
  public void openGlobalLibraries() {
    if (!canShowPsdOrWarnUser(myProject)) return;
    if (!isGradleProjectInAndroidStudio()) {
      myDelegate.openGlobalLibraries();
    }
  }

  @Override
  public void openLibrary(@NotNull Library library) {
    if (!canShowPsdOrWarnUser(myProject)) return;
    if (!isGradleProjectInAndroidStudio()) {
      myDelegate.openLibrary(library);
    }
  }

  @Override
  public boolean canOpenModuleSettings() {
    if (!canShowPsd(myProject)) return false;
    if (isGradleProjectInAndroidStudio()) {
      return true;
    }
    else {
      return myDelegate.canOpenModuleSettings();
    }
  }

  @Override
  public void openModuleLibrarySettings(Module module) {
    if (!canShowPsdOrWarnUser(myProject)) return;
    if (isGradleProjectInAndroidStudio()) {
      openModuleSettings(module);
    }
    else {
      myDelegate.openModuleLibrarySettings(module);
    }
  }

  private void showNewPsd(@NotNull Place place) {
    ProjectStructureConfigurable.getInstance(myProject).showPlace(place);
  }

  @Override
  public void openModuleSettings(Module module) {
    if (!canShowPsdOrWarnUser(myProject)) return;
    if (isGradleProjectInAndroidStudio()) {
      showNewPsd(
        new Place()
          .putPath(ProjectStructureConfigurable.CATEGORY_NAME, ModulesPerspectiveConfigurableKt.MODULES_PERSPECTIVE_DISPLAY_NAME)
          .putPath(BasePerspectiveConfigurableKt.BASE_PERSPECTIVE_MODULE_PLACE_NAME, getGradleIdentityPathOrNull(module))
      );
    }
    else {
      myDelegate.openModuleSettings(module);
    }
  }

  @Override
  public void openSigningConfiguration(@NotNull Module module) {
    showNewPsd(
      new Place()
        .putPath(ProjectStructureConfigurable.CATEGORY_NAME, ModulesPerspectiveConfigurableKt.MODULES_PERSPECTIVE_DISPLAY_NAME)
        .putPath(BasePerspectiveConfigurableKt.BASE_PERSPECTIVE_MODULE_PLACE_NAME, getGradleIdentityPathOrNull(module))
        .putPath(ModulePanelKt.MODULE_PLACE_NAME, SigningConfigsPanelKt.SIGNING_CONFIGS_DISPLAY_NAME)
    );
  }

  @Override
  public void openSdkSettings() {
    showNewPsd(
      new Place()
        .putPath(ProjectStructureConfigurable.CATEGORY_NAME, "SDK Location")
    );
  }

  @Override
  public void chooseJdkLocation() {
    if (myProject != null) {
      showGradleSettings(myProject);
    }
    else {
      showNewPsd(
        new Place()
          .putPath(ProjectStructureConfigurable.CATEGORY_NAME, "SDK Location")
      );
    }
  }

  @Override
  public void openAndSelectDependency(@NotNull Module module, @NotNull GradleCoordinate dependency) {
    showNewPsd(
      new Place()
        .putPath(ProjectStructureConfigurable.CATEGORY_NAME, DependenciesPerspectiveConfigurableKt.DEPENDENCIES_PERSPECTIVE_DISPLAY_NAME)
        .putPath(BasePerspectiveConfigurableKt.BASE_PERSPECTIVE_MODULE_PLACE_NAME, getGradleIdentityPathOrNull(module))
        .putPath(String.format("dependencies.%s.place", getGradleIdentityPathOrNull(module)), dependency.toString())
    );
  }

  public void openAndSelectBuildTypesEditor(@NotNull Module module) {
    showNewPsd(
      new Place()
        .putPath(ProjectStructureConfigurable.CATEGORY_NAME, BuildVariantsPerspectiveConfigurableKt.BUILD_VARIANTS_PERSPECTIVE_DISPLAY_NAME)
        .putPath(BasePerspectiveConfigurableKt.BASE_PERSPECTIVE_MODULE_PLACE_NAME, getGradleIdentityPathOrNull(module))
        .putPath(BuildVariantsPanelKt.BUILD_VARIANTS_PLACE_NAME, BuildTypesPanelKt.BUILD_TYPES_DISPLAY_NAME)
    );
  }

  public void openAndSelectFlavorsEditor(@NotNull Module module) {
    showNewPsd(
      new Place()
        .putPath(ProjectStructureConfigurable.CATEGORY_NAME, BuildVariantsPerspectiveConfigurableKt.BUILD_VARIANTS_PERSPECTIVE_DISPLAY_NAME)
        .putPath(BasePerspectiveConfigurableKt.BASE_PERSPECTIVE_MODULE_PLACE_NAME, getGradleIdentityPathOrNull(module))
        .putPath(BuildVariantsPanelKt.BUILD_VARIANTS_PLACE_NAME, ProductFlavorsPanelKt.PRODUCT_FLAVORS_DISPLAY_NAME)
    );
  }

  @Override
  public void openAndSelectDependenciesEditor(@NotNull Module module) {
    showNewPsd(
      new Place()
        .putPath(ProjectStructureConfigurable.CATEGORY_NAME, DependenciesPerspectiveConfigurableKt.DEPENDENCIES_PERSPECTIVE_DISPLAY_NAME)
        .putPath(BasePerspectiveConfigurableKt.BASE_PERSPECTIVE_MODULE_PLACE_NAME, getGradleIdentityPathOrNull(module))
    );
  }

  @Override
  public boolean canOpenModuleLibrarySettings() {
    if (!canShowPsd(myProject)) return false;
    if (isGradleProjectInAndroidStudio()) {
      return false;
    }
    else {
      return myDelegate.canOpenModuleLibrarySettings();
    }
  }

  @Override
  public boolean canOpenContentEntriesSettings() {
    if (!canShowPsd(myProject)) return false;
    if (isGradleProjectInAndroidStudio()) {
      return false;
    }
    else {
      return myDelegate.canOpenContentEntriesSettings();
    }
  }

  @Override
  public void openContentEntriesSettings(Module module) {
    if (!canShowPsdOrWarnUser(myProject)) return;
    if (isGradleProjectInAndroidStudio()) {
      openModuleSettings(module);
    }
    else {
      myDelegate.openContentEntriesSettings(module);
    }
  }

  @Override
  public boolean canOpenModuleDependenciesSettings() {
    if (!canShowPsd(myProject)) return false;
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
    if (!canShowPsdOrWarnUser(myProject)) return;
    if (isGradleProjectInAndroidStudio()) {
      openModuleSettings(module);
    }
    else {
      myDelegate.openModuleDependenciesSettings(module, orderEntry);
    }
  }

  @Override
  public boolean canOpenLibraryOrSdkSettings(OrderEntry orderEntry) {
    if (!canShowPsd(myProject)) return false;
    if (isGradleProjectInAndroidStudio()) {
      return false;
    }
    else {
      return myDelegate.canOpenLibraryOrSdkSettings(orderEntry);
    }
  }

  @Override
  public void openLibraryOrSdkSettings(@NotNull OrderEntry orderEntry) {
    if (!canShowPsdOrWarnUser(myProject)) return;
    if (!isGradleProjectInAndroidStudio()) {
      myDelegate.openLibraryOrSdkSettings(orderEntry);
    }
  }

  @Override
  public boolean processModulesMoved(Module[] modules, @Nullable ModuleGroup targetGroup) {
    if (!canShowPsd(myProject)) return false;
    if (isGradleProjectInAndroidStudio()) {
      return false;
    }
    else {
      return myDelegate.processModulesMoved(modules, targetGroup);
    }
  }

  @Override
  public void showModuleConfigurationDialog(String moduleToSelect, String editorNameToSelect) {
    if (!canShowPsdOrWarnUser(myProject)) return;
    if (isGradleProjectInAndroidStudio()) {
      Module module = ModuleManager.getInstance(myProject).findModuleByName(moduleToSelect);
      assert module != null;
      showNewPsd(
        new Place()
          .putPath(ProjectStructureConfigurable.CATEGORY_NAME, ModulesPerspectiveConfigurableKt.MODULES_PERSPECTIVE_DISPLAY_NAME)
          .putPath(BasePerspectiveConfigurableKt.BASE_PERSPECTIVE_MODULE_PLACE_NAME, getGradleIdentityPathOrNull(module))
      );
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
    if (!canShowPsdOrWarnUser(myProject)) return;
    if (!isGradleProjectInAndroidStudio()) {
      myDelegate.openArtifactSettings(artifact);
    }
  }

  private boolean isGradleProjectInAndroidStudio() {
    return IdeInfo.getInstance().isAndroidStudio() && ProjectSystemUtil.requiresAndroidModel(myProject);
  }
}
