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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.util.BuildMode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildSettings {
  private static final Key<BuildMode> PROJECT_BUILD_MODE_KEY = Key.create("android.gradle.project.build.mode");
  private static final Key<Module[]> SELECTED_MODULE_NAMES_KEY = Key.create("android.gradle.project.selected.module.names");
  private static final Key<String> RUN_CONFIGURATION_TYPE_ID = Key.create("android.gradle.project.run.configuration.type.id");

  @NotNull private final Project myProject;

  @NotNull
  public static BuildSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, BuildSettings.class);
  }

  public BuildSettings(@NotNull Project project) {
    myProject = project;
  }

  public void clear() {
    setModulesToBuild(null);
    setBuildMode(null);
    setRunConfigurationTypeId(null);
  }

  @Nullable
  public BuildMode getBuildMode() {
    return myProject.getUserData(PROJECT_BUILD_MODE_KEY);
  }

  public void setBuildMode(@Nullable BuildMode action) {
    myProject.putUserData(PROJECT_BUILD_MODE_KEY, action);
  }

  @Nullable
  public Module[] getModulesToBuild() {
    return myProject.getUserData(SELECTED_MODULE_NAMES_KEY);
  }

  public void setModulesToBuild(@Nullable Module[] modules) {
    myProject.putUserData(SELECTED_MODULE_NAMES_KEY, modules);
  }

  @Nullable
  public String getRunConfigurationTypeId() {
    return myProject.getUserData(RUN_CONFIGURATION_TYPE_ID);
  }

  public void setRunConfigurationTypeId(@Nullable String id) {
    myProject.putUserData(RUN_CONFIGURATION_TYPE_ID, id);
  }
}
