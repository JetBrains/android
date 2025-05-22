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
import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.jetbrains.annotations.NotNull;

/**
 * Form that displays a single device ready to be paired via pairing code.
 * Next to the device info, a "Pair" button is displayed.
 */
@UiThread
public class PairingCodeDevicePanel {
  @NotNull private final MdnsService myMdnsService;
  @NotNull private JButton myPairButton;
  @NotNull private JBLabel myDeviceIpLabel;
  @NotNull private JPanel myRootContainer;
  @NotNull private JBLabel myAvailableToPairLabel;

  public PairingCodeDevicePanel(@NotNull MdnsService mdnsService, @NotNull Runnable pairActionRunnable) {
    setupUI();
    myMdnsService = mdnsService;
    myRootContainer.setBorder(JBUI.Borders.empty(5, 10));
    myRootContainer.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myPairButton.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myAvailableToPairLabel.setForeground(UIColors.LIGHT_LABEL);
    RelativeFont.SMALL.install(myAvailableToPairLabel);
    myPairButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        pairActionRunnable.run();
      }
    });
    myDeviceIpLabel.setText(mdnsService.getDisplayString());
  }

  @NotNull
  public Component getComponent() {
    return myRootContainer;
  }

  @NotNull
  public MdnsService getMdnsService() {
    return myMdnsService;
  }

  private void setupUI() {
    myRootContainer = new JPanel();
    myRootContainer.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), 0, 0));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Device at ");
    myRootContainer.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                      0, false));
    final Spacer spacer1 = new Spacer();
    myRootContainer.add(spacer1, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myAvailableToPairLabel = new JBLabel();
    myAvailableToPairLabel.setText("Available to pair");
    myRootContainer.add(myAvailableToPairLabel, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null, 0, false));
    myPairButton = new JButton();
    myPairButton.setText("Pair");
    myRootContainer.add(myPairButton, new GridConstraints(0, 2, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, 1,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDeviceIpLabel = new JBLabel();
    Font myDeviceIpLabelFont = getFont(null, Font.BOLD, -1, myDeviceIpLabel.getFont());
    if (myDeviceIpLabelFont != null) myDeviceIpLabel.setFont(myDeviceIpLabelFont);
    myDeviceIpLabel.setText("xxx-xxx-xxx-xxx");
    myRootContainer.add(myDeviceIpLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
  }

  private Font getFont(String fontName, int style, int size, Font currentFont) {
    if (currentFont == null) return null;
    String resultName;
    if (fontName == null) {
      resultName = currentFont.getName();
    }
    else {
      Font testFont = new Font(fontName, Font.PLAIN, 10);
      if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
        resultName = fontName;
      }
      else {
        resultName = currentFont.getName();
      }
    }
    Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
    Font fontWithFallback = isMac
                            ? new Font(font.getFamily(), font.getStyle(), font.getSize())
                            : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
    return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
  }
}
