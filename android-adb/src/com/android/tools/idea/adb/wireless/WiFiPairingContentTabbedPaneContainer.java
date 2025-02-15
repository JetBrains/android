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
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.AsyncProcessIcon;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.jetbrains.annotations.NotNull;

/**
 * Top level component in each pairing tab. Contains a top and bottom row of fixed size,
 * and a center panel for a custom component.
 */
@UiThread
public class WiFiPairingContentTabbedPaneContainer {
  @NotNull private JPanel myRootComponent;
  @NotNull private JPanel myTopRow;
  @NotNull private JPanel myCenterRow;
  @NotNull private JPanel myBottomRow;
  @NotNull private AsyncProcessIcon myAsyncProcessIcon;
  @NotNull private JBLabel myAsyncProcessText;

  public WiFiPairingContentTabbedPaneContainer() {
    setupUI();
    EditorPaneUtils.setTitlePanelBorder(myTopRow);
    EditorPaneUtils.setBottomPanelBorder(myBottomRow);
    myAsyncProcessIcon.suspend();
    myAsyncProcessIcon.setVisible(false);
  }

  private void createUIComponents() {
    myAsyncProcessIcon = new AsyncProcessIcon("available devices progress");
  }

  public void setParentDisposable(@NotNull Disposable parentDisposable) {
    // Ensure async icon is disposed deterministically
    Disposer.register(parentDisposable, myAsyncProcessIcon);
  }

  public void setAsyncProcessText(@NotNull String text) {
    myAsyncProcessText.setText(text);
    myAsyncProcessIcon.setVisible(true);
    myAsyncProcessIcon.resume();
  }

  public void setContent(@NotNull JComponent component) {
    myCenterRow.removeAll();
    myCenterRow.add(component, BorderLayout.CENTER);
  }

  private void setupUI() {
    createUIComponents();
    myRootComponent = new JPanel();
    myRootComponent.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    myTopRow = new JPanel();
    myTopRow.setLayout(new BorderLayout(0, 0));
    myRootComponent.add(myTopRow, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myTopRow.add(myAsyncProcessIcon, BorderLayout.EAST);
    myAsyncProcessText = new JBLabel();
    Font myAsyncProcessTextFont = getFont(null, Font.BOLD, -1, myAsyncProcessText.getFont());
    if (myAsyncProcessTextFont != null) myAsyncProcessText.setFont(myAsyncProcessTextFont);
    myTopRow.add(myAsyncProcessText, BorderLayout.CENTER);
    myCenterRow = new JPanel();
    myCenterRow.setLayout(new BorderLayout(0, 0));
    myRootComponent.add(myCenterRow, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                         null, null, 0, false));
    myBottomRow = new JPanel();
    myBottomRow.setLayout(new BorderLayout(0, 0));
    myRootComponent.add(myBottomRow, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
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

  public JComponent getRootComponent() { return myRootComponent; }
}
