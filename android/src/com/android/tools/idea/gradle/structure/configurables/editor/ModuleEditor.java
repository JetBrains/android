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
package com.android.tools.idea.gradle.structure.configurables.editor;

import com.android.tools.idea.gradle.structure.configurables.model.ModuleMergedModel;
import com.android.tools.idea.gradle.structure.configurables.editor.dependencies.DependenciesEditor;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModuleElementsEditor;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public abstract class ModuleEditor implements Place.Navigator, Disposable {
  @NonNls protected static final String SELECTED_EDITOR_NAME = "selectedEditor";

  @NotNull private final ModuleMergedModel myModel;
  @NotNull private final List<ModuleConfigurationEditor> myEditors = Lists.newArrayList();

  @Nullable private History myHistory;
  private JPanel mySettingsPanel;

  protected ModuleEditor(@NotNull ModuleMergedModel model) {
    myModel = model;
  }

  public void init(@Nullable History history) {
    setHistory(history);
    for (ModuleConfigurationEditor editor : myEditors) {
      editor.setHistory(myHistory);
    }
    restoreSelectedEditor();
  }

  protected abstract void restoreSelectedEditor();

  @NotNull
  public JPanel getPanel() {
    if (mySettingsPanel == null) {
      createEditors();
      mySettingsPanel = new JPanel(new BorderLayout());
      mySettingsPanel.add(createCenterPanel(), BorderLayout.CENTER);
    }
    return mySettingsPanel;
  }

  private void createEditors() {
    myEditors.add(new DependenciesEditor(myModel));
  }

  @NotNull
  protected abstract JComponent createCenterPanel();


  @Nullable
  public String getHelpTopic() {
    if (myEditors.isEmpty()) {
      return null;
    }
    ModuleConfigurationEditor selectedEditor = getSelectedEditor();
    return selectedEditor != null ? selectedEditor.getHelpTopic() : null;
  }

  @Nullable
  public abstract ModuleConfigurationEditor getSelectedEditor();

  public abstract void selectEditor(@Nullable String displayName);

  @Nullable
  public abstract ModuleConfigurationEditor getEditor(@NotNull String displayName);

  public void apply() throws ConfigurationException {
    for (ModuleConfigurationEditor editor : myEditors) {
      editor.saveData();
      editor.apply();
    }
  }

  public void canApply() throws ConfigurationException {
    for (ModuleConfigurationEditor editor : myEditors) {
      if (editor instanceof ModuleElementsEditor) {
        ((ModuleElementsEditor)editor).canApply();
      }
    }
  }

  @Override
  public void setHistory(@Nullable History history) {
    myHistory = history;
  }

  @Override
  public void dispose() {
    for (ModuleConfigurationEditor editor : myEditors) {
      editor.disposeUIResources();
    }
    myEditors.clear();
    disposeCenterPanel();
  }

  protected abstract void disposeCenterPanel();

  @Nullable
  public History getHistory() {
    return myHistory;
  }

  @NotNull
  public List<ModuleConfigurationEditor> getEditors() {
    return myEditors;
  }
}
