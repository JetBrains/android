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
package com.android.tools.idea.run.profiler;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


/**
 * A Service that manages and persists states of {@link TaskSettingConfig}.
 */
@State(name = "TaskSettingConfigsState", storages = @Storage("cpuProfilingConfigs.xml"))
public class TaskSettingConfigsState implements PersistentStateComponent<TaskSettingConfigsState> {
  @NotNull
  private List<TaskSettingConfig> myUserConfigs;

  // Default/updated configs for Task-based UX
  @NotNull
  private List<TaskSettingConfig> myTaskConfigs;

  /**
   * Default constructor is required and used by {@link PersistentStateComponent}.
   *
   * @see <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html">IntelliJ Platform SDK DevGuide</a>
   */
  public TaskSettingConfigsState() {
    myUserConfigs = new ArrayList<>();
    myTaskConfigs = new ArrayList<>();
  }

  @NotNull
  public static TaskSettingConfigsState getInstance(Project project) {
    return project.getService(TaskSettingConfigsState.class);
  }

  @NotNull
  public List<TaskSettingConfig> getUserConfigs() {
    return myUserConfigs;
  }

  public void setUserConfigs(@NotNull List<TaskSettingConfig> configs) {
    myUserConfigs = configs;
  }

  @NotNull
  public List<TaskSettingConfig> getTaskConfigs() {
    if (myTaskConfigs.isEmpty()) {
      return getTaskDefaultConfigs();
    }
    return myTaskConfigs;
  }

  public void setTaskConfigs(@NotNull List<TaskSettingConfig> configs) {
    myTaskConfigs = configs;
  }

  /**
   * @param config - adds the given {@param config} if there is no configuration with the same name
   *                (including default configurations), otherwise ignores.
   * @return true if the config was added.
   */
  public boolean addUserConfig(@NotNull TaskSettingConfig config) {
    if (getConfigByName(config.getName()) == null) {
      myUserConfigs.add(config);
      return true;
    }
    return false;
  }

  @NotNull
  public static List<TaskSettingConfig> getDefaultConfigs() {
    ImmutableList.Builder<TaskSettingConfig> configs = new ImmutableList.Builder<TaskSettingConfig>()
      .add(new TaskSettingConfig(TaskSettingConfig.Technology.SAMPLED_NATIVE))
      .add(new TaskSettingConfig(TaskSettingConfig.Technology.SYSTEM_TRACE))
      .add(new TaskSettingConfig(TaskSettingConfig.Technology.INSTRUMENTED_JAVA))
      .add(new TaskSettingConfig(TaskSettingConfig.Technology.SAMPLED_JAVA));
    return configs.build();
  }

  @NotNull
  public static List<TaskSettingConfig> getTaskDefaultConfigs() {
    //Exclude System Trace config since it doesn't have any updatable attribute
    ImmutableList.Builder<TaskSettingConfig> configs = new ImmutableList.Builder<TaskSettingConfig>()
      .add(new TaskSettingConfig(TaskSettingConfig.Technology.SAMPLED_NATIVE))
      .add(new TaskSettingConfig(TaskSettingConfig.Technology.INSTRUMENTED_JAVA))
      .add(new TaskSettingConfig(TaskSettingConfig.Technology.SAMPLED_JAVA));
    return configs.build();
  }

  @NotNull
  public List<TaskSettingConfig> getConfigs() {
    return ImmutableList.<TaskSettingConfig>builder().addAll(getDefaultConfigs()).addAll(getUserConfigs()).build();
  }

  @Nullable
  public TaskSettingConfig getConfigByName(@NotNull String name) {
    return getConfigs().stream()
      .filter(c -> name.equals(c.getName()))
      .findFirst()
      .orElse(null);
  }

  @Nullable
  @Override
  public TaskSettingConfigsState getState() {
    return this;
  }

  @Override
  public void loadState(TaskSettingConfigsState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
