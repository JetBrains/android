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
package com.android.tools.idea.adb.wireless;

import com.intellij.ui.components.JBTabbedPane;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class PairingContentPanel {
  @NotNull private JPanel myRootContainer;
  @NotNull private JBTabbedPane myTabbedPane;
  @NotNull private PairingContentTabbedPaneContainer myQrCodePanel;
  @NotNull private PairingContentTabbedPaneContainer myPinCodePanel;

  @NotNull
  public JComponent getComponent() {
    return myRootContainer;
  }

  public void setQrCodeComponent(@NotNull JComponent component) {
    myQrCodePanel.setContent(component);
  }

  public void setPinCodeComponent(@NotNull JComponent component) {
    myPinCodePanel.setContent(component);
  }
}
