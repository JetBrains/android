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
package com.android.tools.idea.structure;

import com.android.SdkConstants;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A standard {@linkplan Configurable} instance that shows panels for editing a single Android Gradle module in Project Structure.
 */
public class AndroidModuleConfigurable extends NamedConfigurable {
  private final String myDisplayName;
  private final AndroidModuleEditor myModuleEditor;
  private final Module myModule;

  public AndroidModuleConfigurable(Project project, Module module, String modulePath) {
    myDisplayName = modulePath.substring(modulePath.lastIndexOf(SdkConstants.GRADLE_PATH_SEPARATOR) + 1);
    myModuleEditor = new AndroidModuleEditor(project, modulePath);
    myModule = module;
  }

  @Override
  public void setDisplayName(String name) {
  }

  @Override
  public Object getEditableObject() {
    return myModule;
  }

  @Override
  public String getBannerSlogan() {
    return "Module '" + myDisplayName + "'";
  }

  @Override
  public JComponent createOptionsPanel() {
    return myModuleEditor.getPanel();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public boolean isModified() {
    return myModuleEditor.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myModuleEditor.apply();
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myModuleEditor);
  }

  @Nullable
  @Override
  public Icon getIcon(boolean expanded) {
    return AllIcons.Nodes.Module;
  }

  public void selectDependency(@NotNull String dependency) {
    myModuleEditor.selectDependency(dependency);
  }
}
