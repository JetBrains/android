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
package com.android.tools.idea.databinding.config;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.databinding.InternalDataBindingUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import org.jetbrains.annotations.TestOnly;

public final class DataBindingConfigurable implements SearchableConfigurable {
  private JPanel myRootPanel;
  private JBRadioButton myGeneratedCodeRadioButton;
  private JBRadioButton myLiveCodeRadioButton;
  private JBLabel restartWarning;

  private DataBindingConfiguration myConfiguration;

  public DataBindingConfigurable() {
    myConfiguration = DataBindingConfiguration.getInstance();
    updateUi();
  }

  private void updateUi() {
    switch (myConfiguration.CODE_GEN_MODE) {
      case IN_MEMORY:
        myLiveCodeRadioButton.setSelected(true);
        break;
      case ON_DISK:
        myGeneratedCodeRadioButton.setSelected(true);
        break;
    }
    restartWarning.setForeground(JBColor.RED);
  }

  @NotNull
  @Override
  public String getId() {
    return "android.databinding";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myRootPanel;
  }

  @Override
  public boolean isModified() {
    boolean modified = myConfiguration.CODE_GEN_MODE != getSelectedCodeGenMode();
    restartWarning.setVisible(modified);
    return modified;
  }

  @Override
  public void apply() throws ConfigurationException {
    DataBindingConfiguration.CodeGenMode codeGenMode = getSelectedCodeGenMode();
    ApplicationManager.getApplication().runWriteAction(() -> {
      myConfiguration.CODE_GEN_MODE = codeGenMode;
      InternalDataBindingUtil.recalculateEnableInMemoryClassGeneration();
    });
  }

  @Override
  public void reset() {
    updateUi();
  }

  @Override
  public void disposeUIResources() {
  }

  @Nls
  @Override
  public String getDisplayName() {
    return IdeInfo.getInstance().isAndroidStudio() ? "Data Binding" : "Android Data Binding";
  }

  @TestOnly
  @NotNull
  public JBRadioButton getGeneratedCodeRadioButton() {
    return myGeneratedCodeRadioButton;
  }

  @TestOnly
  @NotNull
  public JBRadioButton getLiveCodeRadioButton() {
    return myLiveCodeRadioButton;
  }

  private DataBindingConfiguration.CodeGenMode getSelectedCodeGenMode() {
    if (myGeneratedCodeRadioButton.isSelected()) {
      return DataBindingConfiguration.CodeGenMode.ON_DISK;
    }
    return DataBindingConfiguration.CodeGenMode.IN_MEMORY;
  }
}
