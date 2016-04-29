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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import com.android.tools.idea.gradle.structure.configurables.model.ModuleMergedModel;
import com.android.tools.idea.gradle.structure.configurables.editor.ModuleElementsEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DependenciesEditor extends ModuleElementsEditor {
  private DependenciesPanel myPanel;

  public DependenciesEditor(@NotNull ModuleMergedModel model) {
    super(model);
  }

  @Override
  @Nullable
  protected JComponent doCreateComponent() {
    myPanel = new DependenciesPanel(this, getModel());
    return myPanel;
  }

  @Override
  public void saveData() {
  }

  @Override
  public void moduleStateChanged() {
  }

  @Override
  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("modules.classpath.title");
  }

  @Override
  @Nullable
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
}
