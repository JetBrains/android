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
package com.android.tools.idea.gradle.compiler;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Configuration page for Gradle compiler settings.
 */
public class GradleCompilerSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final GradleCompilerConfiguration myConfig;

  private JSpinner myHeapSizeSpinner;
  private JPanel myContentPanel;
  private JSpinner myPermGenSizeSpinner;

  public GradleCompilerSettingsConfigurable(@NotNull Project project) {
    myConfig = GradleCompilerConfiguration.getInstance(project);
  }

  @Override
  @NotNull
  public String getId() {
    return "gradle.compiler";
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Gradle";
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @Nullable
  public JComponent createComponent() {
    return myContentPanel;
  }

  @Override
  public boolean isModified() {
    return myConfig.MAX_HEAP_SIZE != getMaxHeapSize() || myConfig.MAX_PERM_GEN_SIZE != getMaxPermGenSize();
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfig.MAX_HEAP_SIZE = getMaxHeapSize();
    myConfig.MAX_PERM_GEN_SIZE = getMaxPermGenSize();
  }

  private int getMaxHeapSize() {
    return (Integer) myHeapSizeSpinner.getValue();
  }

  private int getMaxPermGenSize() {
    return (Integer) myPermGenSizeSpinner.getValue();
  }

  @NotNull
  private static SpinnerModel createNumberModel(int value) {
    return new SpinnerNumberModel(value, 1, 10000000, 1);
  }

  @Override
  public void reset() {
    myHeapSizeSpinner.setModel(createNumberModel(myConfig.MAX_HEAP_SIZE));
    myPermGenSizeSpinner.setModel(createNumberModel(myConfig.MAX_PERM_GEN_SIZE));
  }

  @Override
  public void disposeUIResources() {
  }
}
