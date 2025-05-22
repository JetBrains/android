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
import com.android.tools.idea.ui.JSingleDigitTextField;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.AsyncProcessIcon;
import icons.StudioIcons;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.jetbrains.annotations.NotNull;

/**
 * Form that allows entering 6 digit pairing code, as well as displaying
 * pairing status and result.
 */
@UiThread
public class PairingCodeInputPanel {
  public static final int PAIRING_CODE_DIGIT_COUNT = 6;
  @NotNull private JPanel myRootContainer;
  @NotNull private JBLabel myDeviceAddressLabel;
  @NotNull private JBLabel myPairingProgressLabel;
  @NotNull private AsyncProcessIcon myPairingProgressAsyncIcon;
  @NotNull private JBLabel myStatusIconLabel;
  @NotNull private JSingleDigitTextField myPairingCodeDigit1;
  @NotNull private JSingleDigitTextField myPairingCodeDigit2;
  @NotNull private JSingleDigitTextField myPairingCodeDigit3;
  @NotNull private JSingleDigitTextField myPairingCodeDigit4;
  @NotNull private JSingleDigitTextField myPairingCodeDigit5;
  @NotNull private JSingleDigitTextField myPairingCodeDigit6;
  @NotNull private final List<JSingleDigitTextField> myAllDigitTextFields;

  public PairingCodeInputPanel(@NotNull Disposable parentDisposable) {
    setupUI();
    myPairingProgressLabel.setText("");
    myPairingProgressAsyncIcon.setVisible(false);
    myStatusIconLabel.setVisible(false);

    myAllDigitTextFields =
      Arrays.asList(myPairingCodeDigit1, myPairingCodeDigit2, myPairingCodeDigit3, myPairingCodeDigit4, myPairingCodeDigit5,
                    myPairingCodeDigit6);
    for (int i = 0; i < myAllDigitTextFields.size(); i++) {
      myAllDigitTextFields.get(i).setName("PairingCode-Digit-" + i);
    }

    // Ensure async icon is disposed deterministically
    Disposer.register(parentDisposable, myPairingProgressAsyncIcon);
  }

  private void createUIComponents() {
    myPairingProgressAsyncIcon = new AsyncProcessIcon("pairing code pairing progress");
    myPairingCodeDigit1 = new JSingleDigitTextField();
    myPairingCodeDigit2 = new JSingleDigitTextField();
    myPairingCodeDigit3 = new JSingleDigitTextField();
    myPairingCodeDigit4 = new JSingleDigitTextField();
    myPairingCodeDigit5 = new JSingleDigitTextField();
    myPairingCodeDigit6 = new JSingleDigitTextField();
  }

  @NotNull
  public JComponent getComponent() {
    return myRootContainer;
  }

  @NotNull
  public JSingleDigitTextField getLastPairingCodeDigitComponent() {
    return myAllDigitTextFields.get(myAllDigitTextFields.size() - 1);
  }

  @NotNull
  public JSingleDigitTextField getFirstPairingCodeDigitComponent() {
    return myAllDigitTextFields.get(0);
  }

  @NotNull
  public String getPairingCode() {
    StringBuilder sb = new StringBuilder();
    myAllDigitTextFields.forEach(textField -> {
      String text = textField.getText();
      if (text == null || text.isEmpty()) {
        text = "0";
      }
      sb.append(text);
    });
    return sb.toString();
  }

  public void setDevice(@NotNull MdnsService service) {
    myDeviceAddressLabel.setText(service.getDisplayString());
  }

  public void showProgress(@NotNull String text) {
    myStatusIconLabel.setVisible(false);
    myPairingProgressAsyncIcon.setVisible(true);
    myPairingProgressLabel.setText(text);
  }

  public void showSuccess(@NotNull AdbOnlineDevice device) {
    myStatusIconLabel.setIcon(StudioIcons.Common.SUCCESS);
    myStatusIconLabel.setVisible(true);
    myPairingProgressAsyncIcon.setVisible(false);
    myPairingProgressLabel.setText(String.format("%s connected", device.getDisplayString()));
  }

  public void showPairingError() {
    myStatusIconLabel.setIcon(StudioIcons.Common.ERROR);
    myStatusIconLabel.setVisible(true);
    myPairingProgressAsyncIcon.setVisible(false);
    myPairingProgressLabel.setText("There was an error pairing the device");
  }

  public void setDigitsEnabled(boolean enabled) {
    myAllDigitTextFields.forEach(textField -> textField.setEnabled(enabled));
  }

  private void setupUI() {
    createUIComponents();
    myRootContainer = new JPanel();
    myRootContainer.setLayout(new GridLayoutManager(8, 3, new Insets(0, 0, 0, 0), -1, -1));
    final Spacer spacer1 = new Spacer();
    myRootContainer.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(0, 20), null, 0, false));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Enter the 6 digit code shown on the device at");
    myRootContainer.add(jBLabel1, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                      0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), 0, 0));
    myRootContainer.add(panel1, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
    myDeviceAddressLabel = new JBLabel();
    Font myDeviceAddressLabelFont = getFont(null, Font.BOLD, -1, myDeviceAddressLabel.getFont());
    if (myDeviceAddressLabelFont != null) myDeviceAddressLabel.setFont(myDeviceAddressLabelFont);
    myDeviceAddressLabel.setText("[xxx.xxx.xxx.xxx]");
    panel1.add(myDeviceAddressLabel,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText(" to pair.");
    panel1.add(jBLabel2,
               new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    myRootContainer.add(spacer2, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(0, 30), null, 1, false));
    final Spacer spacer3 = new Spacer();
    myRootContainer.add(spacer3, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridBagLayout());
    myRootContainer.add(panel2, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
    myPairingCodeDigit1.setColumns(2);
    myPairingCodeDigit1.setHorizontalAlignment(0);
    GridBagConstraints gbc;
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    panel2.add(myPairingCodeDigit1, gbc);
    myPairingCodeDigit2.setColumns(2);
    myPairingCodeDigit2.setHorizontalAlignment(0);
    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.insets = new Insets(0, 2, 0, 0);
    panel2.add(myPairingCodeDigit2, gbc);
    myPairingCodeDigit3.setColumns(2);
    myPairingCodeDigit3.setHorizontalAlignment(0);
    gbc = new GridBagConstraints();
    gbc.gridx = 2;
    gbc.gridy = 0;
    gbc.insets = new Insets(0, 2, 0, 0);
    panel2.add(myPairingCodeDigit3, gbc);
    myPairingCodeDigit4.setColumns(2);
    myPairingCodeDigit4.setHorizontalAlignment(0);
    gbc = new GridBagConstraints();
    gbc.gridx = 3;
    gbc.gridy = 0;
    gbc.insets = new Insets(0, 2, 0, 0);
    panel2.add(myPairingCodeDigit4, gbc);
    myPairingCodeDigit5.setColumns(2);
    myPairingCodeDigit5.setHorizontalAlignment(0);
    gbc = new GridBagConstraints();
    gbc.gridx = 4;
    gbc.gridy = 0;
    gbc.insets = new Insets(0, 2, 0, 0);
    panel2.add(myPairingCodeDigit5, gbc);
    myPairingCodeDigit6.setColumns(2);
    myPairingCodeDigit6.setHorizontalAlignment(0);
    gbc = new GridBagConstraints();
    gbc.gridx = 5;
    gbc.gridy = 0;
    gbc.insets = new Insets(0, 2, 0, 0);
    panel2.add(myPairingCodeDigit6, gbc);
    final Spacer spacer4 = new Spacer();
    myRootContainer.add(spacer4, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), 0, 0));
    myRootContainer.add(panel3, new GridConstraints(6, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
    myPairingProgressLabel = new JBLabel();
    myPairingProgressLabel.setText("(some text)");
    panel3.add(myPairingProgressLabel,
               new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel4 = new JPanel();
    panel4.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    panel3.add(panel4,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                   false));
    panel4.add(myPairingProgressAsyncIcon,
               new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                   false));
    myStatusIconLabel = new JBLabel();
    panel4.add(myStatusIconLabel,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer5 = new Spacer();
    panel4.add(spacer5, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_FIXED, 1, null, new Dimension(2, 0), null, 0, false));
    final Spacer spacer6 = new Spacer();
    myRootContainer.add(spacer6, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final Spacer spacer7 = new Spacer();
    myRootContainer.add(spacer7, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(1, 10), null, 0, false));
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
