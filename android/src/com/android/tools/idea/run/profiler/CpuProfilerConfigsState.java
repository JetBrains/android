// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.run.profiler;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * A Service that manages and persists states of {@link CpuProfilerConfig}.
 */
@State(name = "CpuProfilerConfigsState", storages = @Storage("cpuProfilingConfigs.xml"))
public class CpuProfilerConfigsState implements PersistentStateComponent<CpuProfilerConfigsState> {
  @NotNull
  private List<CpuProfilerConfig> myUserConfigs;

  /**
   * Default constructor is required and used by {@link PersistentStateComponent}.
   *
   * @see <a href="https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html">Persisting State of Components (IntelliJ Platform Docs)</a>
   */
  public CpuProfilerConfigsState() {
    myUserConfigs = new ArrayList<>();
  }

  @NotNull
  public static CpuProfilerConfigsState getInstance(Project project) {
    return project.getService(CpuProfilerConfigsState.class);
  }

  @NotNull
  public List<CpuProfilerConfig> getUserConfigs() {
    return myUserConfigs;
  }

  public void setUserConfigs(@NotNull List<CpuProfilerConfig> configs) {
    myUserConfigs = configs;
  }

  /**
   * @param config - adds the given {@param config} if there is no configuration with the same name
   *                (including default configurations), otherwise ignores.
   * @return true if the config was added.
   */
  public boolean addUserConfig(@NotNull CpuProfilerConfig config) {
    if (getConfigByName(config.getName()) == null) {
      myUserConfigs.add(config);
      return true;
    }
    return false;
  }

  @NotNull
  public static List<CpuProfilerConfig> getDefaultConfigs() {
    ImmutableList.Builder<CpuProfilerConfig> configs = new ImmutableList.Builder<CpuProfilerConfig>()
      .add(new CpuProfilerConfig(CpuProfilerConfig.Technology.SAMPLED_NATIVE))
      .add(new CpuProfilerConfig(CpuProfilerConfig.Technology.SYSTEM_TRACE))
      .add(new CpuProfilerConfig(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA))
      .add(new CpuProfilerConfig(CpuProfilerConfig.Technology.SAMPLED_JAVA));
    return configs.build();
  }

  @NotNull
  public List<CpuProfilerConfig> getConfigs() {
    return ImmutableList.<CpuProfilerConfig>builder().addAll(getDefaultConfigs()).addAll(getUserConfigs()).build();
  }

  @Nullable
  public CpuProfilerConfig getConfigByName(@NotNull String name) {
    return getConfigs().stream()
      .filter(c -> name.equals(c.getName()))
      .findFirst()
      .orElse(null);
  }

  @Nullable
  @Override
  public CpuProfilerConfigsState getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull CpuProfilerConfigsState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}