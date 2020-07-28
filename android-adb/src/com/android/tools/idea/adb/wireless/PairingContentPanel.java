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

import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import org.jetbrains.annotations.NotNull;

public class PairingContentPanel {
  @NotNull private JPanel myQrCodePanel;
  @NotNull private JPanel myRootContainer;
  @NotNull private JBLabel myQrCodeTitle;
  @NotNull private JBLabel myPinCodeLabel;
  @NotNull private JPanel myPinCodePanel;
  @NotNull private JPanel myHelpLinkPanel;
  @NotNull private JPanel myQrCodeTitlePanel;
  @NotNull private JPanel myPinCodeTitlePanel;
  @NotNull private JPanel myBottomLeftPanel;

  public PairingContentPanel() {
    EditorPaneUtils.setTitlePanelBorder(myQrCodeTitlePanel, 0);
    EditorPaneUtils.setTitlePanelBorder(myPinCodeTitlePanel, 1);
    setBottomPanelBorder(myBottomLeftPanel, 0);
    setBottomPanelBorder(myHelpLinkPanel, 1);
    myPinCodePanel.setBorder(new CustomLineBorder(UIColors.ONE_PIXEL_DIVIDER, 0, 1, 0, 0));
  }

  private static void setBottomPanelBorder(@NotNull JComponent panel, int leftPixels) {
    Border line = new CustomLineBorder(UIColors.ONE_PIXEL_DIVIDER, 1, leftPixels, 0, 0);
    Border c = new CompoundBorder(line, JBUI.Borders.empty(5, 10));
    panel.setBorder(c);
    panel.setMinimumSize(new JBDimension(0, 30));
  }

  @NotNull
  public JComponent getComponent() {
    return myRootContainer;
  }

  public void setQrCodeComponent(@NotNull JComponent component) {
    myQrCodePanel.removeAll();
    myQrCodePanel.add(component, BorderLayout.CENTER);
  }

  public void setPinCodeComponent(@NotNull JComponent component) {
    myPinCodePanel.removeAll();
    myPinCodePanel.add(component, BorderLayout.CENTER);
  }

  public void setHelpLinkComponent(@NotNull JComponent component) {
    myHelpLinkPanel.removeAll();
    myHelpLinkPanel.add(component, BorderLayout.EAST);
  }
}
