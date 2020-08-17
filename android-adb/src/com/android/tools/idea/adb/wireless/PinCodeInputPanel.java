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

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.AsyncProcessIcon;
import icons.StudioIcons;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;

public class PinCodeInputPanel {
  public static final int PIN_CODE_DIGIT_COUNT = 6;
  @NotNull private JPanel myRootContainer;
  @NotNull private JBLabel myDeviceAddressLabel;
  @NotNull private JBLabel myPairingProgressLabel;
  @NotNull private AsyncProcessIcon myPairingProgressAsyncIcon;
  @NotNull private JBLabel myStatusIconLabel;
  @NotNull private JTextField myPinCodeDigit1;
  @NotNull private JTextField myPinCodeDigit2;
  @NotNull private JTextField myPinCodeDigit3;
  @NotNull private JTextField myPinCodeDigit4;
  @NotNull private JTextField myPinCodeDigit5;
  @NotNull private JTextField myPinCodeDigit6;
  @NotNull private final List<JTextField> myAllDigitTextFields;

  public PinCodeInputPanel() {
    myPairingProgressLabel.setText("");
    myPairingProgressAsyncIcon.setVisible(false);
    myStatusIconLabel.setVisible(false);

    myAllDigitTextFields =
      Arrays.asList(myPinCodeDigit1, myPinCodeDigit2, myPinCodeDigit3, myPinCodeDigit4, myPinCodeDigit5, myPinCodeDigit6);
    for (int i = 0; i < myAllDigitTextFields.size(); i++) {
      myAllDigitTextFields.get(i).setName("PinCode-Digit-" + i);
    }
  }

  private void createUIComponents() {
    myPairingProgressAsyncIcon = new AsyncProcessIcon("pin code pairing progress");
    myPinCodeDigit1 = new JSingleDigitTextField();
    myPinCodeDigit2 = new JSingleDigitTextField();
    myPinCodeDigit3 = new JSingleDigitTextField();
    myPinCodeDigit4 = new JSingleDigitTextField();
    myPinCodeDigit5 = new JSingleDigitTextField();
    myPinCodeDigit6 = new JSingleDigitTextField();
  }

  @NotNull
  public JComponent getComponent() {
    return myRootContainer;
  }

  @NotNull
  public JComponent getPinCodeComponent() {
    return myAllDigitTextFields.get(0);
  }

  @NotNull
  public String getPinCode() {
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
}
