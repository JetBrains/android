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
import com.intellij.util.ui.AsyncProcessIcon;
import icons.StudioIcons;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
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
}
