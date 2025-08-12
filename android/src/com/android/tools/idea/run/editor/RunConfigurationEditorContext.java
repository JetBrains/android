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
package com.android.tools.idea.run.editor;

import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.module.Module;
import java.awt.event.ActionListener;
import javax.swing.JComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunConfigurationEditorContext implements DeployTargetConfigurableContext {
  private final ConfigurationModuleSelector myModuleSelector;
  private final JComboBox myCombo;

  public RunConfigurationEditorContext(@NotNull ConfigurationModuleSelector moduleSelector, @NotNull JComboBox moduleSelectorCombo) {
    myModuleSelector = moduleSelector;
    myCombo = moduleSelectorCombo;
  }

  @Nullable
  @Override
  public Module getModule() {
    return myModuleSelector.getModule();
  }

  @Override
  public void addModuleChangeListener(@NotNull ActionListener listener) {
    myCombo.addActionListener(listener);
  }

  @Override
  public void removeModuleChangeListener(@NotNull ActionListener listener) {
    myCombo.removeActionListener(listener);
  }
}
