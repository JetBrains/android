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
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.jetbrains.annotations.NotNull;

/**
 * Form that wraps a {@link PairingCodeContentPanel} with instructions text at the bottom.
 * This is the main contents of the "Pair with pairing code" tab pane.
 */
@UiThread
public class PairingCodeTabPanel {
  @NotNull private final Consumer<MdnsService> myPairingCodePairInvoked;
  @NotNull private final PairingCodeContentPanel myContentPanel;
  @NotNull private JBLabel myFirstLineLabel;
  @NotNull private JBLabel mySecondLineLabel;
  @NotNull private JPanel myRootComponent;
  @NotNull private JPanel myContentPanelContainer;

  public PairingCodeTabPanel(@NotNull Consumer<MdnsService> pairingCodePairInvoked) {
    setupUI();
    myPairingCodePairInvoked = pairingCodePairInvoked;
    myContentPanelContainer.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myRootComponent.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myFirstLineLabel.setForeground(UIColors.PAIRING_HINT_LABEL);
    mySecondLineLabel.setForeground(UIColors.PAIRING_HINT_LABEL);

    myContentPanel = new PairingCodeContentPanel();
    myContentPanelContainer.add(myContentPanel.getComponent(), BorderLayout.CENTER);

    showAvailableServices(new ArrayList<>());
  }

  public void showAvailableServices(@NotNull List<MdnsService> devices) {
    myContentPanel.showDevices(devices, myPairingCodePairInvoked);
  }

  @NotNull
  public JComponent getComponent() {
    return myRootComponent;
  }

  private void setupUI() {
    myRootComponent = new JPanel();
    myRootComponent.setLayout(new GridLayoutManager(5, 1, new Insets(0, 0, 0, 0), 0, 0));
    myFirstLineLabel = new JBLabel();
    Font myFirstLineLabelFont = getFont(null, Font.BOLD, -1, myFirstLineLabel.getFont());
    if (myFirstLineLabelFont != null) myFirstLineLabel.setFont(myFirstLineLabelFont);
    myFirstLineLabel.setText("Set your Android 11+ device to pairing mode");
    myRootComponent.add(myFirstLineLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
    mySecondLineLabel = new JBLabel();
    mySecondLineLabel.setText("Go to Developer options > Wireless debugging > Pair device with pairing code");
    myRootComponent.add(mySecondLineLabel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myRootComponent.add(spacer1, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_FIXED, new Dimension(5, 20), null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    myRootComponent.add(spacer2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_FIXED, new Dimension(0, 4), null, null, 0, false));
    myContentPanelContainer = new JPanel();
    myContentPanelContainer.setLayout(new BorderLayout(0, 0));
    myRootComponent.add(myContentPanelContainer, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_WANT_GROW,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
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
