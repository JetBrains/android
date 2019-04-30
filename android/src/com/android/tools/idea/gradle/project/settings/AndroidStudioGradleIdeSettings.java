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

import static com.intellij.util.xmlb.XmlSerializerUtil.copyBean;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * Android Studio-specific Gradle project settings.
 */
@State(
  name="AndroidStudioGradleSettings",
  storages = @Storage(file = "android.gradle.studio.xml")
)
public class AndroidStudioGradleIdeSettings implements PersistentStateComponent<AndroidStudioGradleIdeSettings> {
  @NotNull private final CurrentTimeProvider myCurrentTimeProvider;

  @SuppressWarnings("unused")
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
  public void loadState(@NotNull AndroidStudioGradleIdeSettings state) {
    copyBean(state, this);
  }

  static class CurrentTimeProvider {
    long getCurrentTimeMillis() {
      return System.currentTimeMillis();
    }
  }
}
