/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.xmlb.XmlSerializerUtil.copyBean;

/**
 * Android Studio-specific Gradle project settings.
 */
@State(
  name="AndroidStudioGradleSettings"
)
public class AndroidStudioGradleProjectSettings implements PersistentStateComponent<AndroidStudioGradleProjectSettings> {
  public boolean DISABLE_EMBEDDED_MAVEN_REPO;

  @NotNull
  public static AndroidStudioGradleProjectSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidStudioGradleProjectSettings.class);
  }

  @Override
  @NotNull
  public AndroidStudioGradleProjectSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull AndroidStudioGradleProjectSettings state) {
    copyBean(state, this);
  }
}
