/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.flags;

import com.android.tools.idea.compose.ComposeExperimentalConfiguration;
import com.intellij.openapi.options.UnnamedConfigurable;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

public class ComposeExperimentalSettingsConfigurable implements ExperimentalConfigurable {
  private JPanel myPanel;
  private JCheckBox myPreviewPickerCheckBox;
  private final ComposeExperimentalConfiguration mySettings;

  public ComposeExperimentalSettingsConfigurable() {
    mySettings = ComposeExperimentalConfiguration.getInstance();
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return mySettings.isPreviewPickerEnabled() != myPreviewPickerCheckBox.isSelected();
  }

  public void apply() {
    mySettings.setPreviewPickerEnabled(myPreviewPickerCheckBox.isSelected());
  }

  public void reset() {
    myPreviewPickerCheckBox.setSelected(mySettings.isPreviewPickerEnabled());
  }
}
