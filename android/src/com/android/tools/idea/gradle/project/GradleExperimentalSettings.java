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

import com.android.tools.idea.flags.ExperimentalSettingsConfigurable.TraceProfileItem;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name = "GradleExperimentalSettings",
  storages = {
    @Storage("gradle.experimental.xml")}
)
public class GradleExperimentalSettings implements PersistentStateComponent<GradleExperimentalSettings> {
  public boolean USE_L2_DEPENDENCIES_ON_SYNC = true;
  public boolean SKIP_GRADLE_TASKS_LIST = true;

  public boolean ENABLE_PARALLEL_SYNC = true;

  public boolean ENABLE_GRADLE_API_OPTIMIZATION = true;

  // Settings related to Gradle sync tracer.
  public boolean TRACE_GRADLE_SYNC = false;
  public TraceProfileItem TRACE_PROFILE_SELECTION = TraceProfileItem.DEFAULT;
  public String TRACE_PROFILE_LOCATION = "";

  @NotNull
  public static GradleExperimentalSettings getInstance() {
    return ApplicationManager.getApplication().getService(GradleExperimentalSettings.class);
  }

  @Override
  @NotNull
  public GradleExperimentalSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull GradleExperimentalSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
