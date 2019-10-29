/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.facet;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_APP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_FEATURE;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of {@link FacetConfiguration} for {@link AndroidFacet}.
 *
 * <p>Stores configuration by serializing {@link AndroidFacetProperties} with {@link PersistentStateComponent}.
 *
 * <p>Avoid using instances of this class if at all possible. This information should be provided by
 * {@link com.android.tools.idea.projectsystem.AndroidProjectSystem} and it is up to the project system used by the project to choose how
 * this information is obtained and persisted.
 */
public class AndroidFacetConfiguration implements FacetConfiguration, PersistentStateComponent<AndroidFacetProperties> {
  private static final FacetEditorTab[] NO_EDITOR_TABS = new FacetEditorTab[0];

  @NotNull private AndroidFacetProperties myProperties = new AndroidFacetProperties();

  public void init(@NotNull Module module, @NotNull VirtualFile contentRoot) {
    init(module, contentRoot.getPath());
  }

  public void init(@NotNull Module module, @NotNull String baseDirectoryPath) {
    final String s = AndroidRootUtil.getPathRelativeToModuleDir(module, baseDirectoryPath);
    if (s == null || s.isEmpty()) {
      return;
    }
    myProperties.GEN_FOLDER_RELATIVE_PATH_APT = '/' + s + myProperties.GEN_FOLDER_RELATIVE_PATH_APT;
    myProperties.GEN_FOLDER_RELATIVE_PATH_AIDL = '/' + s + myProperties.GEN_FOLDER_RELATIVE_PATH_AIDL;
    myProperties.MANIFEST_FILE_RELATIVE_PATH = '/' + s + myProperties.MANIFEST_FILE_RELATIVE_PATH;
    myProperties.RES_FOLDER_RELATIVE_PATH = '/' + s + myProperties.RES_FOLDER_RELATIVE_PATH;
    myProperties.ASSETS_FOLDER_RELATIVE_PATH = '/' + s + myProperties.ASSETS_FOLDER_RELATIVE_PATH;
    myProperties.LIBS_FOLDER_RELATIVE_PATH = '/' + s + myProperties.LIBS_FOLDER_RELATIVE_PATH;
    myProperties.PROGUARD_LOGS_FOLDER_RELATIVE_PATH = '/' + s + myProperties.PROGUARD_LOGS_FOLDER_RELATIVE_PATH;

    for (int i = 0; i < myProperties.RES_OVERLAY_FOLDERS.size(); i++) {
      myProperties.RES_OVERLAY_FOLDERS.set(i, '/' + s + myProperties.RES_OVERLAY_FOLDERS.get(i));
    }
  }

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    AndroidFacetProperties state = getState();
    //noinspection deprecation  This is one of legitimate assignments to this property.
    if (state.ALLOW_USER_CONFIGURATION) {
      return new FacetEditorTab[]{new AndroidFacetEditorTab(editorContext, this)};
    }
    return NO_EDITOR_TABS;
  }

  public boolean isImportedProperty(@NotNull AndroidImportableProperty property) {
    return !myProperties.myNotImportedProperties.contains(property);
  }

  public boolean isIncludeAssetsFromLibraries() {
    return myProperties.myIncludeAssetsFromLibraries;
  }

  public void setIncludeAssetsFromLibraries(boolean includeAssetsFromLibraries) {
    myProperties.myIncludeAssetsFromLibraries = includeAssetsFromLibraries;
  }

  public boolean isAppProject() {
    int projectType = getState().PROJECT_TYPE;
    return projectType == PROJECT_TYPE_APP || projectType == PROJECT_TYPE_INSTANTAPP;
  }

  public boolean isAppOrFeature() {
    int projectType = getState().PROJECT_TYPE;
    return projectType == PROJECT_TYPE_APP ||
           projectType == PROJECT_TYPE_INSTANTAPP ||
           projectType == PROJECT_TYPE_FEATURE ||
           projectType == PROJECT_TYPE_DYNAMIC_FEATURE;
  }

  @NotNull
  @Override
  public AndroidFacetProperties getState() {
    return myProperties;
  }

  @Override
  public void loadState(@NotNull AndroidFacetProperties properties) {
    myProperties = properties;
  }

  public boolean canBeDependency() {
    int projectType = getState().PROJECT_TYPE;
    return projectType == PROJECT_TYPE_LIBRARY || projectType == PROJECT_TYPE_FEATURE;
  }

  public boolean isLibraryProject() {
    return getState().PROJECT_TYPE == PROJECT_TYPE_LIBRARY;
  }

  public int getProjectType() {
    return getState().PROJECT_TYPE;
  }

  public void setProjectType(int type) {
    getState().PROJECT_TYPE = type;
  }
}
