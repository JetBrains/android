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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

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
  @NotNull private AndroidFacetProperties myProperties = new AndroidFacetProperties();

  /**
   * Application service implemented in JPS code.
   *
   * <p>Most projects don't display facet UI and have no need for this. But legacy projects based on JPS have to provide a
   * {@link FacetEditorTab} and the implementation of it is tied to JPS, which means in cannot live in the common module.
   */
  interface EditorTabProvider {
    FacetEditorTab createFacetEditorTab(@NotNull FacetEditorContext editorContext, @NotNull AndroidFacetConfiguration configuration);
  }

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    AndroidFacetProperties state = getState();
    //noinspection deprecation  This is one of legitimate assignments to this property.
    if (state.ALLOW_USER_CONFIGURATION) {
      EditorTabProvider editorTabProvider = ApplicationManager.getApplication().getService(EditorTabProvider.class);
      if (editorTabProvider != null) {
        return new FacetEditorTab[]{editorTabProvider.createFacetEditorTab(editorContext, this)};
      }
    }
    return new FacetEditorTab[]{new NotEditableAndroidFacetEditorTab()};
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

  @VisibleForTesting
  public void setProjectType(int type) {
    getState().PROJECT_TYPE = type;
  }
}
