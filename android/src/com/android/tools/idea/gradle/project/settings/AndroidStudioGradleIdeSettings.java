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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.xmlb.XmlSerializerUtil.copyBean;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Android Studio-specific Gradle project settings.
 */
@State(
  name="AndroidStudioGradleSettings",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/android.gradle.studio.xml")
)
public class AndroidStudioGradleIdeSettings implements PersistentStateComponent<AndroidStudioGradleIdeSettings> {
  public boolean ENABLE_EMBEDDED_MAVEN_REPO;
  public long EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS = -1;

  @NotNull private final CurrentTimeProvider myCurrentTimeProvider;

  public AndroidStudioGradleIdeSettings() {
    this(new CurrentTimeProvider());
  }

  @VisibleForTesting
  AndroidStudioGradleIdeSettings(@NotNull CurrentTimeProvider currentTimeProvider) {
    myCurrentTimeProvider = currentTimeProvider;
  }

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
    if (ENABLE_EMBEDDED_MAVEN_REPO) {
      if (EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS == -1) {
        EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS = myCurrentTimeProvider.getCurrentTimeMillis();
      }
      long timePassedMillis = myCurrentTimeProvider.getCurrentTimeMillis() - EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS;
      long daysPassed = MILLISECONDS.toDays(timePassedMillis);
      ENABLE_EMBEDDED_MAVEN_REPO = daysPassed <= 14; // Disable offline repo after 2 weeks
    }
    return ENABLE_EMBEDDED_MAVEN_REPO;
  }

  public void setEmbeddedMavenRepoEnabled(boolean value) {
    ENABLE_EMBEDDED_MAVEN_REPO = value;
    EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS = value ? myCurrentTimeProvider.getCurrentTimeMillis() : -1;
  }

  static class CurrentTimeProvider {
    long getCurrentTimeMillis() {
      return System.currentTimeMillis();
    }
  }
}
