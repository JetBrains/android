/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project;

import com.android.tools.idea.gradle.structure.model.PsProject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ProjectDependenciesConfigurable extends NamedConfigurable<PsProject> {
  @NotNull private final PsProject myProject;

  private String myDisplayName;
  private ProjectDependenciesPanel myDependenciesPanel;

  public ProjectDependenciesConfigurable(@NotNull PsProject project) {
    myProject = project;
    setDisplayName("<All Modules>");
  }

  @Override
  public PsProject getEditableObject() {
    return myProject;
  }

  @Override
  public String getBannerSlogan() {
    return myDisplayName;
  }

  @Override
  public JComponent createOptionsPanel() {
    if (myDependenciesPanel == null) {
      myDependenciesPanel = new ProjectDependenciesPanel(myProject);
    }
    return myDependenciesPanel;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public void setDisplayName(String name) {
    myDisplayName = name;
  }

  @Override
  @NotNull
  public Icon getIcon(boolean expanded) {
    return AllIcons.Nodes.ModuleGroup;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {

  }

  @Override
  public void reset() {

  }

  @Override
  public void disposeUIResources() {
    if (myDependenciesPanel != null) {
      Disposer.dispose(myDependenciesPanel);
    }
  }
}
