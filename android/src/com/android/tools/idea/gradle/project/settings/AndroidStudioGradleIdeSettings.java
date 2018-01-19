/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.xmlb.XmlSerializerUtil.copyBean;

/**
 * Android Studio-specific Gradle project settings.
 */
@State(
  name="AndroidStudioGradleSettings",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/android.gradle.studio.xml")
)
public class AndroidStudioGradleIdeSettings implements PersistentStateComponent<AndroidStudioGradleIdeSettings> {
  public boolean ENABLE_EMBEDDED_MAVEN_REPO;

  @NotNull
  public static AndroidStudioGradleIdeSettings getInstance() {
    return ServiceManager.getService(AndroidStudioGradleIdeSettings.class);
  }

  @Override
  @NotNull
  public AndroidStudioGradleIdeSettings getState() {
    return this;
  }

  @Override
  public void loadState(AndroidStudioGradleIdeSettings state) {
    copyBean(state, this);
  }

  public boolean isEmbeddedMavenRepoEnabled() {
    return ENABLE_EMBEDDED_MAVEN_REPO;
  }
}
