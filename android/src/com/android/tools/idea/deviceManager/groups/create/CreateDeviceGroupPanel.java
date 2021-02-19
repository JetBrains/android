/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.groups.create;

import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class CreateDeviceGroupPanel {
  @NotNull private JPanel myRootComponent;
  @NotNull private JBTextField myNameTextField;
  @NotNull private JBTextArea myDescriptionTextArea;
  @NotNull private AddDevicesToGroupPanel myAddDevicesToGroupPanel;

  @NotNull JPanel getComponent() {
    return myRootComponent;
  }

  private void createUIComponents() {
    myAddDevicesToGroupPanel = new AddDevicesToGroupPanel();
  }

  @NotNull AddDevicesToGroupPanel getAddDevicesToGroupPanel() {
    return myAddDevicesToGroupPanel;
  }

  @NotNull String getNameValue() {
    return myNameTextField.getText();
  }

  @NotNull String getDescriptionValue() {
    return myDescriptionTextArea.getText();
  }
}
