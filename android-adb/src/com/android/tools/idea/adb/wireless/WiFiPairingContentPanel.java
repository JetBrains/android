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

import com.android.annotations.concurrency.UiThread;
import com.intellij.openapi.Disposable;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Form that displays a {@link com.intellij.ui.components.JBTabbedPane} with one
 * tab for {@link QrCodeTabPanel} and one tab for {@link PairingCodeTabPanel}.
 */
@UiThread
public class WiFiPairingContentPanel {
  @NotNull private JPanel myRootContainer;
  @NotNull private WiFiPairingContentTabbedPaneContainer myQrCodePanel;
  @NotNull private WiFiPairingContentTabbedPaneContainer myPairingCodePanel;

  public WiFiPairingContentPanel(@NotNull Disposable parentDisposable) {
    setupUI();
    myPairingCodePanel.setParentDisposable(parentDisposable);
    myQrCodePanel.setParentDisposable(parentDisposable);
  }

  @NotNull
  public JComponent getComponent() {
    return myRootContainer;
  }

  public void setQrCodeComponent(@NotNull JComponent component) {
    myQrCodePanel.setContent(component);
  }

  public void setPairingCodeComponent(@NotNull JComponent component) {
    myPairingCodePanel.setContent(component);
    myPairingCodePanel.setAsyncProcessText("Available Wi-Fi devices");
  }

  private void setupUI() {
    myRootContainer = new JPanel();
    myRootContainer.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), 0, 0));
    final JBTabbedPane jBTabbedPane1 = new JBTabbedPane();
    jBTabbedPane1.setTabComponentInsets(new Insets(0, 0, 0, 0));
    myRootContainer.add(jBTabbedPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           null, new Dimension(200, 200), null, 0, false));
    myQrCodePanel = new WiFiPairingContentTabbedPaneContainer();
    jBTabbedPane1.addTab("Pair using QR code", myQrCodePanel.getRootComponent());
    myPairingCodePanel = new WiFiPairingContentTabbedPaneContainer();
    jBTabbedPane1.addTab("Pair using pairing code", myPairingCodePanel.getRootComponent());
  }
}
