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
package com.android.tools.idea.gradle.project.facet.gradle;

import com.android.tools.idea.gradle.structure.editors.AndroidModuleEditor;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * For IntelliJ IDEA only, not used in Android Studio
 *
 * @author Eugene.Kudelevsky
 */
public class AndroidGradleFacetEditorTab extends FacetEditorTab {
  private final AndroidModuleEditor myModuleEditor;

  public AndroidGradleFacetEditorTab(@NotNull Project project, @NotNull String gradleProjectPath) {
    myModuleEditor = new AndroidModuleEditor(project, gradleProjectPath);
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Android Gradle Module Settings";
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    return myModuleEditor.getPanel();
  }

  @Override
  public void apply() throws ConfigurationException {
    myModuleEditor.apply();
  }

  @Override
  public boolean isModified() {
    return myModuleEditor.isModified();
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myModuleEditor);
  }
}
