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
package com.android.tools.idea.gradle.structure.editors;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleCoordinate;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon;
import static com.intellij.openapi.vfs.ReadonlyStatusHandler.ensureFilesWritable;

/**
 * A standard {@linkplan Configurable} instance that shows panels for editing a single Android Gradle module in Project Structure.
 */
public class AndroidModuleConfigurable extends NamedConfigurable {
  private final String myDisplayName;
  private final AndroidModuleEditor myModuleEditor;
  private final Module myModule;

  public AndroidModuleConfigurable(Project project, Module module, String modulePath) {
    String moduleName = modulePath.substring(modulePath.lastIndexOf(SdkConstants.GRADLE_PATH_SEPARATOR) + 1);
    myDisplayName = moduleName.isEmpty() ? project.getName() : moduleName;
    myModuleEditor = new AndroidModuleEditor(project, modulePath);
    myModule = module;
  }

  public Module getModule() {
    return myModule;
  }

  @Override
  public void setDisplayName(String name) {
  }

  @Override
  public Object getEditableObject() {
    return getModule();
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

  @Override
  public boolean isModified() {
    return myModuleEditor.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    VirtualFile file = getGradleBuildFile(myModule);
    if (file != null && !ensureFilesWritable(myModule.getProject(), file)) {
      throw new ConfigurationException(String.format("Build file %1$s is not writable", file.getPath()));
    }
    myModuleEditor.apply();
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myModuleEditor);
  }

  @Nullable
  @Override
  public Icon getIcon(boolean expanded) {
    // Use Android Studio icons
    return getModuleIcon(myModule);
  }

  public void selectDependency(@NotNull GradleCoordinate dependency) {
    myModuleEditor.selectDependency(dependency);
  }

  public void selectDependenciesTab() {
    myModuleEditor.selectDependenciesTab();
  }

  public void selectBuildTypesTab() {
    myModuleEditor.selectBuildTypesTab();
  }

  public void selectFlavorsTab() {
    myModuleEditor.selectFlavorsTab();
  }

  public void openSigningConfiguration() {
    myModuleEditor.openSigningConfiguration();
  }
}
