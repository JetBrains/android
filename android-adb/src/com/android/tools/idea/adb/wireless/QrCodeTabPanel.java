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
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.AsyncProcessIcon;
import icons.StudioIcons;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
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
    setupUI();
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

  private void setupUI() {
    createUIComponents();
    myRootComponent = new JPanel();
    myRootComponent.setLayout(new GridLayoutManager(14, 3, new Insets(0, 0, 0, 0), -1, -1));
    final Spacer spacer1 = new Spacer();
    myRootComponent.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_FIXED, new Dimension(5, 15), null, null, 0, false));
    myTopLabel1 = new JBLabel();
    myTopLabel1.setText("<html>To pair an <b>Android 11+</b> device<html>");
    myRootComponent.add(myTopLabel1, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                         null, 0, false));
    final Spacer spacer2 = new Spacer();
    myRootComponent.add(spacer2, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_FIXED, new Dimension(5, 4), null, null, 0, false));
    myTopLabel2 = new JBLabel();
    myTopLabel2.setText("scan the QR code from your device");
    myRootComponent.add(myTopLabel2, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                         null, 0, false));
    final Spacer spacer3 = new Spacer();
    myRootComponent.add(spacer3, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(5, 20), null, null, 0, false));
    final Spacer spacer4 = new Spacer();
    myRootComponent.add(spacer4, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_CAN_GROW, 1, new Dimension(5, 5), null, null, 0, false));
    myQrCodePanel = new ScalingImagePanel();
    myRootComponent.add(myQrCodePanel, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                           new Dimension(100, 100), null, null, 0, false));
    final Spacer spacer5 = new Spacer();
    myRootComponent.add(spacer5, new GridConstraints(5, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_CAN_GROW, 1, new Dimension(5, 5), null, null, 0, false));
    final Spacer spacer6 = new Spacer();
    myRootComponent.add(spacer6, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(5, 20), null, null, 0, false));
    myPairingStatusPanel = new JPanel();
    myPairingStatusPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), 0, 0));
    myRootComponent.add(myPairingStatusPanel,
                        new GridConstraints(7, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myPairingStatusIconPanel = new JPanel();
    myPairingStatusIconPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myPairingStatusPanel.add(myPairingStatusIconPanel,
                             new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                 false));
    myPairingStatusIconLabel = new JBLabel();
    myPairingStatusIconPanel.add(myPairingStatusIconLabel,
                                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    myPairingStatusIconPanel.add(myPairingStatusProcessIcon,
                                 new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     null, null, 0, false));
    final Spacer spacer7 = new Spacer();
    myPairingStatusIconPanel.add(spacer7, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_FIXED, 1, null, new Dimension(2, 0), null, 0,
                                                              false));
    myPairingStatusLabel = new JBLabel();
    myPairingStatusLabel.setText("(some text)");
    myPairingStatusPanel.add(myPairingStatusLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                       null, null, null, 0, false));
    myScanNewDevicePanel = new JPanel();
    myScanNewDevicePanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), 0, 0));
    myRootComponent.add(myScanNewDevicePanel, new GridConstraints(8, 0, 1, 3, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                  null, null, null, 0, false));
    myScanNewDevicePanel.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    myScanNewDeviceLink = new LinkLabel();
    myScanNewDeviceLink.setText("Scan new device");
    myScanNewDevicePanel.add(myScanNewDeviceLink,
                             new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, 1,
                                                 GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer8 = new Spacer();
    myRootComponent.add(spacer8, new GridConstraints(9, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_FIXED, new Dimension(0, 30), null, null, 0, false));
    myFirstLineLabel = new JBLabel();
    Font myFirstLineLabelFont = getFont(null, Font.BOLD, -1, myFirstLineLabel.getFont());
    if (myFirstLineLabelFont != null) myFirstLineLabel.setFont(myFirstLineLabelFont);
    myFirstLineLabel.setText("QR scanner available at:");
    myRootComponent.add(myFirstLineLabel, new GridConstraints(10, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
    final Spacer spacer9 = new Spacer();
    myRootComponent.add(spacer9, new GridConstraints(11, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_FIXED, new Dimension(0, 4), null, null, 0, false));
    mySecondLineLabel = new JBLabel();
    mySecondLineLabel.setText("Developer options > Wireless debugging > Pair using QR code");
    myRootComponent.add(mySecondLineLabel, new GridConstraints(12, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               null, null, 0, false));
    final Spacer spacer10 = new Spacer();
    myRootComponent.add(spacer10, new GridConstraints(13, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                      GridConstraints.SIZEPOLICY_FIXED, new Dimension(5, 20), null, null, 0, false));
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
