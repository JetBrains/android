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
import com.android.tools.adtui.ui.SVGScaledImageProvider;
import com.android.tools.adtui.ui.ScalingImagePanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.AsyncProcessIcon;
import icons.StudioIcons;
import java.awt.Font;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

@UiThread
public class QrCodeTabPanel {
  @NotNull private final Logger LOG = Logger.getInstance(QrCodeTabPanel.class);
  @NotNull private JBLabel myTopLabel1;
  @NotNull private JBLabel myTopLabel2;
  @NotNull private JBLabel myFirstLineLabel;
  @NotNull private JBLabel mySecondLineLabel;
  @NotNull private ScalingImagePanel myQrCodePanel;
  @NotNull private JPanel myRootComponent;
  @NotNull private JPanel myPairingStatusPanel;
  @NotNull private JPanel myPairingStatusIconPanel;
  @NotNull private JBLabel myPairingStatusIconLabel;
  @NotNull private AsyncProcessIcon myPairingStatusProcessIcon;
  @NotNull private JBLabel myPairingStatusLabel;
  @NotNull private JPanel myScanNewDevicePanel;
  @NotNull private LinkLabel<Void> myScanNewDeviceLink;

  public QrCodeTabPanel(@NotNull Runnable scanAnotherDeviceRunnable, @NotNull Disposable parentDisposable) {
    myRootComponent.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myQrCodePanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);

    myPairingStatusPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myPairingStatusIconPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myPairingStatusIconLabel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myPairingStatusProcessIcon.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myScanNewDevicePanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myScanNewDeviceLink.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);

    myPairingStatusLabel.setForeground(UIColors.PAIRING_STATUS_LABEL);
    myScanNewDeviceLink.setForeground(UIColors.PAIRING_STATUS_LABEL);
    myFirstLineLabel.setForeground(UIColors.PAIRING_HINT_LABEL);
    mySecondLineLabel.setForeground(UIColors.PAIRING_HINT_LABEL);

    myScanNewDeviceLink.setListener((aSource, aLinkData) -> scanAnotherDeviceRunnable.run(), null);
    myScanNewDeviceLink.setIcon(null); // Don't show default "Link" icon

    Disposer.register(parentDisposable, myPairingStatusProcessIcon);
    Disposer.register(parentDisposable, myQrCodePanel);
  }

  private void createUIComponents() {
    myPairingStatusProcessIcon = new AsyncProcessIcon("pairing code pairing progress");
  }

  @NotNull
  public JPanel getComponent() {
    return myRootComponent;
  }

  public void setQrCode(@NotNull QrCodeImage image) {
    LOG.info("New QR Code generated: " + image.getPairingString());
    myQrCodePanel.setScaledImageProvider(null);
    myQrCodePanel.setImage(image.getImage());
  }

  /**
   * When waiting for device to scan, just show the QR core image
   */
  public void showQrCodePairingStarted() {
    myQrCodePanel.setActive(true);
    myTopLabel1.setVisible(true);
    myTopLabel2.setVisible(true);

    // Note: Add a "space" so the label size does not become empty and causes a re-layout
    setBold(myPairingStatusLabel, false);
    myPairingStatusLabel.setText(" ");
    myPairingStatusIconLabel.setVisible(false);
    myPairingStatusProcessIcon.setVisible(false);

    myScanNewDeviceLink.setVisible(false);
  }

  /**
   * When device scanned the QR code, gray out the QR code image, and show
   * a "processing" animated icon
   */
  public void showQrCodePairingInProgress() {
    myQrCodePanel.setActive(false);

    setBold(myPairingStatusLabel, true);
    myPairingStatusLabel.setText("Connecting to device. This takes up to 2 minutes.");
    myPairingStatusIconLabel.setVisible(false);
    myPairingStatusProcessIcon.setVisible(true);

    myScanNewDeviceLink.setVisible(false);
  }

  /**
   * Same as {@link #showQrCodePairingInProgress()}
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
    myQrCodePanel.setScaledImageProvider(SVGScaledImageProvider.create(StudioIcons.Common.SUCCESS));
    myQrCodePanel.setActive(true);

    myTopLabel1.setVisible(false);
    myTopLabel2.setVisible(false);
    myPairingStatusIconLabel.setVisible(false);
    myPairingStatusProcessIcon.setVisible(false);
    setBold(myPairingStatusLabel, true);
    myPairingStatusLabel.setText(device.getDisplayString() + " connected");

    myScanNewDeviceLink.setVisible(true);
  }

  /**
   * When device is connected, show an error message with an "error" icon,
   * and keep the QR code grayed out
   */
  public void showQrCodePairingError() {
    myQrCodePanel.setActive(false);

    myTopLabel1.setVisible(false);
    myTopLabel2.setVisible(false);
    myPairingStatusIconLabel.setVisible(true);
    myPairingStatusIconLabel.setIcon(StudioIcons.Common.ERROR);
    myPairingStatusProcessIcon.setVisible(false);
    setBold(myPairingStatusLabel, false);
    myPairingStatusLabel.setText("An error occurred connecting device. Scan to try again.");
    //TODO: Add a "troubleshoot connection" hyperlink

    myScanNewDeviceLink.setVisible(true);
  }

  public void setBold(JBLabel label, boolean isBold) {
    if (isBold) {
      label.setFont(label.getFont().deriveFont(Font.BOLD));
    } else {
      label.setFont(label.getFont().deriveFont(Font.PLAIN));
    }
  }
}
