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
import com.android.tools.adtui.ui.ImagePanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.AsyncProcessIcon;
import icons.StudioIcons;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

@UiThread
public class QrCodePanel {
  @NotNull private final Logger LOG = Logger.getInstance(QrCodePanel.class);
  @NotNull private JBLabel myFirstLineLabel;
  @NotNull private JBLabel mySecondLineLabel;
  @NotNull private ImagePanel myQrCodePanel;
  @NotNull private JPanel myRootComponent;
  @NotNull private JPanel myPairingStatusPanel;
  @NotNull private JPanel myPairingStatusIconPanel;
  @NotNull private JBLabel myPairingStatusIconLabel;
  @NotNull private AsyncProcessIcon myPairingStatusProcessIcon;
  @NotNull private JBLabel myPairingStatusLabel;
  @NotNull private JPanel myScanAnotherDevicePanel;
  @NotNull private JButton myScanAnotherDeviceButton;

  public QrCodePanel(@NotNull Runnable scanAnotherDeviceRunnable) {
    myRootComponent.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myQrCodePanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myPairingStatusPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myPairingStatusIconPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myPairingStatusIconLabel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myPairingStatusProcessIcon.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myScanAnotherDevicePanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myScanAnotherDeviceButton.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);

    myPairingStatusLabel.setForeground(UIColors.PAIRING_STATUS_LABEL);
    myScanAnotherDeviceButton.setForeground(UIColors.PAIRING_STATUS_LABEL);
    myFirstLineLabel.setForeground(UIColors.PAIRING_HINT_LABEL);
    mySecondLineLabel.setForeground(UIColors.PAIRING_HINT_LABEL);

    myScanAnotherDeviceButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        scanAnotherDeviceRunnable.run();
      }
    });
  }

  private void createUIComponents() {
    myPairingStatusProcessIcon = new AsyncProcessIcon("pin code pairing progress");
  }

  @NotNull
  public JPanel getComponent() {
    return myRootComponent;
  }

  public void setQrCode(@NotNull QrCodeImage image) {
    LOG.info("New QR Code generated: " + image.getPairingString());
    myQrCodePanel.setImage(image.getImage());
  }

  /**
   * When waiting for device to scan, just show the QR core image
   */
  public void showQrCodePairingStarted() {
    myQrCodePanel.setActive(true);

    // Note: Add a "space" so the label size does not become empty and causes a re-layout
    myPairingStatusLabel.setText(" ");
    myPairingStatusIconLabel.setVisible(false);
    myPairingStatusProcessIcon.setVisible(false);

    myScanAnotherDeviceButton.setVisible(false);
  }

  /**
   * When device scanned the QR code, gray out the QR code image, and show
   * a "processing" animated icon
   */
  public void showQrCodePairingInProgress() {
    myQrCodePanel.setActive(false);

    myPairingStatusLabel.setText("Connected. Gathering device information.");
    myPairingStatusIconLabel.setVisible(false);
    myPairingStatusProcessIcon.setVisible(true);

    myScanAnotherDeviceButton.setVisible(false);
  }

  /**
   * Same as {@link #showQrCodePairingWaitForDevice()}
   */
  public void showQrCodePairingWaitForDevice() {
    //TODO: Current UX Mocks don't show 2 separate states for this
    showQrCodePairingInProgress();
  }

  /**
   * When device is connected, show a confirmation message with a success icon,
   * and keep the QR code grayed out
   */
  public void showQrCodePairingSuccess(@NotNull AdbOnlineDevice device) {
    myQrCodePanel.setActive(false);

    myPairingStatusIconLabel.setVisible(true);
    //TODO: Show a big "V" green icon instead
    myPairingStatusIconLabel.setIcon(StudioIcons.Common.SUCCESS);
    myPairingStatusProcessIcon.setVisible(false);
    myPairingStatusLabel.setText(device.getDisplayString() + " connected");

    myScanAnotherDeviceButton.setVisible(true);
  }

  /**
   * When device is connected, show an error message with an "error" icon,
   * and keep the QR code grayed out
   */
  public void showQrCodePairingError() {
    myQrCodePanel.setActive(false);

    myPairingStatusIconLabel.setVisible(true);
    myPairingStatusIconLabel.setIcon(StudioIcons.Common.ERROR);
    myPairingStatusProcessIcon.setVisible(false);
    myPairingStatusLabel.setText("An error occurred connecting device");
    //TODO: Add a "troubleshoot connection" hyperlink

    myScanAnotherDeviceButton.setVisible(true);
  }
}
