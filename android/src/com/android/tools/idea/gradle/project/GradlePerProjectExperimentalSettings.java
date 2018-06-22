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
package com.android.tools.idea.gradle.project;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Persistent storage of per-project experimental feature settings.
 */
@State(
  name = "GradlePerProjectExperimentalSettings",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  }
)

public class GradlePerProjectExperimentalSettings implements PersistentStateComponent<GradlePerProjectExperimentalSettings> {
  public boolean USE_SINGLE_VARIANT_SYNC;

  @NotNull
  public static GradlePerProjectExperimentalSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradlePerProjectExperimentalSettings.class);
  }

  @Override
  @NotNull
  public GradlePerProjectExperimentalSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull GradlePerProjectExperimentalSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
