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

import com.android.tools.adtui.ui.ImagePanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class QrCodePanel {
  @NotNull private final Logger LOG = Logger.getInstance(QrCodePanel.class);
  @NotNull private JBLabel myFirstLineLabel;
  @NotNull private JBLabel mySecondLineLabel;
  @NotNull private ImagePanel myQrCodePanel;
  @NotNull private JPanel myRootComponent;
  @NotNull private JBLabel myPairingStateLabel;

  public QrCodePanel() {
    myQrCodePanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myRootComponent.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myPairingStateLabel.setForeground(UIColors.PAIRING_STATUS_LABEL);
    myFirstLineLabel.setForeground(UIColors.PAIRING_HINT_LABEL);
    mySecondLineLabel.setForeground(UIColors.PAIRING_HINT_LABEL);
  }

  @NotNull
  public JPanel getComponent() {
    return myRootComponent;
  }

  public void setStatusLabel(@NotNull String line) {
    myPairingStateLabel.setText(line);
  }

  public void setQrCode(@NotNull QrCodeImage image) {
    LOG.info("New QR Code generated: " + image.getPairingString());
    myQrCodePanel.setImage(image.getImage());
  }
}
