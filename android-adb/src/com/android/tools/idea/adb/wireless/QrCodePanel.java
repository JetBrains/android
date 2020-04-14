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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class QrCodePanel {
  @NotNull private static Logger LOG = Logger.getInstance(QrCodePanel.class);
  private JBLabel myFirstLineLabel;
  private JBLabel mySecondLineLabel;
  private ImagePanel myQrCodePanel;
  private JPanel myRootComponent;

  public QrCodePanel() {
    myQrCodePanel.setBorder(JBUI.Borders.customLine(QrCodeColors.BORDER));
    myQrCodePanel.setBackground(QrCodeColors.BACKGROUND);
    myFirstLineLabel.setForeground(QrCodeColors.LABEL_FOREGROUND);
    mySecondLineLabel.setForeground(QrCodeColors.LABEL_FOREGROUND);
  }

  public void setLabels(@NotNull String line1, @NotNull String line2) {
    myFirstLineLabel.setText(line1);
    mySecondLineLabel.setText(line2);
  }

  public void setQrCode(@NotNull QrCodeImage image) {
    LOG.info("New QR Code generated: " + image.getPairingString());
    myQrCodePanel.setImage(image.getImage());
  }

  @NotNull
  public JPanel getComponent() {
    return myRootComponent;
  }
}
