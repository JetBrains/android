/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.mockup.editor;

import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.avdmanager.AvdWizardUtils;
import com.android.tools.idea.avdmanager.AvdManagerUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.device.DeviceArtPainter;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.ide.common.rendering.HardwareConfigHelper.*;

/**
 * Popup that let the user choose a device for the screen view that matches the
 * image ratio.
 */
@SuppressWarnings("unchecked")
public class DeviceSelectionPopup extends DialogWrapper {
  private static final String OK_BUTTON_TEXT = "OK";
  private static final String TITLE = "Change Device to Match Mockup";
  private static final String PROMPT_HELP_TEXT = "Only displaying devices with same aspect ratio as the image.";
  private static final JBColor ERROR_COLOR = JBColor.RED;
  private static final String NO_DEVICE_MESSAGE = "No device or AVD matching the image dimension has been found.\n" +
                                                  "Create a new AVD with the same aspect ratio as the image or choose another image for better result.";
  private static final Color MESSAGE_COLOR = JBColor.foreground();
  private static final Logger LOGGER = Logger.getInstance(DeviceSelectionPopup.class);

  private final Configuration myConfiguration;
  private final Dimension myImageSize;
  private final BufferedImage myImage;

  private JPanel myPanel;
  private JComboBox myDevicesComboBox;
  private JButton myNewDeviceButton;
  private JLabel promptLabel;
  private JPanel myPreviewPanel;
  private JTextPane myMessage;
  private List<Device> myMatchingDevices;
  @Nullable private Device mySelectedDevice;
  private ScreenOrientation myScreenOrientation;
  private BufferedImage myDeviceFrame;
  private Map<String, BufferedImage> myImageCache;
  private boolean myNoMatchingDevice;

  @Nullable
  @Override
  protected String getHelpId() {
    return null;
  }

  public DeviceSelectionPopup(Project project,
                              Configuration configuration,
                              BufferedImage image) {
    super(project, true);
    myConfiguration = configuration;
    myImage = image;
    myMatchingDevices = new ArrayList<>();
    myImageCache = new HashMap<>();
    myImageSize = new Dimension(image.getWidth(), image.getHeight());
    mySelectedDevice = configuration.getDevice();
    setTitle(TITLE);
    setHorizontalStretch(1.2f);
    myHelpAction.setEnabled(false);
    setOKButtonText(OK_BUTTON_TEXT);
    init();
  }

  /**
   * Fill the device combo box by looking first for device matching the same size
   * as the image. If none are found, try to find device with the same aspect ratio.
   * If none are found again, tries to find avd matching size or ratio.
   * Otherwise, use the current selected device as the only item in the combo box
   *
   * @param configuration
   */
  private void initDeviceList(Configuration configuration) {
    myNoMatchingDevice = false;
    if (!myMatchingDevices.isEmpty()) {
      myMatchingDevices.clear();
    }
    if (myDevicesComboBox.getItemCount() > 0) {
      myDevicesComboBox.removeAllItems();
    }
    int lastNexusIndex = 0;
    int lastMatchIndex = 0;
    int lastNexusRatioIndex = 0;

    ConfigurationManager configurationManager = configuration.getConfigurationManager();
    List<Device> deviceList = configurationManager.getDevices();
    final double imageRatio = myImageSize.width / (double)myImageSize.height;
    myScreenOrientation = myImageSize.width <= myImageSize.height
                          ? ScreenOrientation.PORTRAIT
                          : ScreenOrientation.LANDSCAPE;
    for (Device device : deviceList) {
      final Dimension screenSize = device.getScreenSize(myScreenOrientation);
      if (screenSize == null) {
        continue;
      }
      if (myImageSize.equals(screenSize)) {
        if (isNexus(device)) {
          myMatchingDevices.add(lastNexusIndex++, device);
        }
        else if (isGeneric(device)) {
          myMatchingDevices.add(lastNexusIndex + lastMatchIndex++, device);
        }
      }
      else {
        if (ratioAlmostEqual(imageRatio, screenSize)) {
          if (isNexus(device)) {
            myMatchingDevices.add(lastNexusIndex + lastMatchIndex + lastNexusRatioIndex++, device);
          }
          else {
            myMatchingDevices.add(device);
          }
        }
      }
    }

    // if not physical device match the size of the mockup
    // Try to find on in the Avd
    if (myMatchingDevices.isEmpty()) {
      if (findMatchingAvd(configurationManager, imageRatio)) {
        return;
      }
      // If there is still no device matching the size or ratio of the mockup,
      // use the current selected device as best matching device.
      myMatchingDevices.add(configuration.getDevice());
      myNoMatchingDevice = true;
    }

    for (Device device : myMatchingDevices) {
      String deviceLabel;
      if (isNexus(device)) {
        deviceLabel = getNexusLabel(device);
      }
      else if (isGeneric(device)) {
        deviceLabel = getGenericLabel(device);
      }
      else {
        deviceLabel = device.getId();
      }
      if (device == myConfiguration.getDevice()) {
        // Set a special label for the current device
        // and display it on top if it matches the image ratio
        deviceLabel = String.format("* %s (current)", deviceLabel);
      }
      myDevicesComboBox.addItem(deviceLabel);
    }
    mySelectedDevice = myMatchingDevices.get(0);
    myDevicesComboBox.setSelectedIndex(0);
    updateDevicePreview();
  }

  /**
   * Find a avd matching the image size or ratio.
   *
   * @param configurationManager configuration managet to get the Android Facet
   * @param imageRatio           ratio of the imgage
   * @return true if a matching avd has been found
   */
  private boolean findMatchingAvd(ConfigurationManager configurationManager, double imageRatio) {
    AndroidFacet facet = AndroidFacet.getInstance(configurationManager.getModule());
    if (facet == null) {
      // Unlikely, but has happened - see http://b.android.com/68091
      return false;
    }
    final AvdManager avdManager = AvdManagerUtils.getAvdManagerSilently(facet);
    if (avdManager != null) {
      final AvdInfo[] allAvds = avdManager.getAllAvds();
      for (AvdInfo avd : allAvds) {
        Device device = configurationManager.createDeviceForAvd(avd);
        if (device != null) {
          final Dimension screenSize = device.getScreenSize(myScreenOrientation);
          if (myImageSize.equals(screenSize) || ratioAlmostEqual(imageRatio, screenSize)) {
            String avdName = "AVD: " + avd.getName();
            myDevicesComboBox.addItem(avdName);
            myMatchingDevices.add(device);
          }
        }
      }
    }
    return !myMatchingDevices.isEmpty();
  }

  private static boolean ratioAlmostEqual(double imageRatio, Dimension screenSize) {
    return Math.abs(screenSize.width / (double)screenSize.height - imageRatio) < 0.01;
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDevicesComboBox;
  }

  @Override
  protected JComponent createCenterPanel() {
    initDeviceList(myConfiguration);
    initMessage();
    initDeviceComboBox();
    initNewButton();
    return myPanel;
  }

  private void initNewButton() {
    myNewDeviceButton.addActionListener(e -> {
      ModelWizardDialog dialog = AvdWizardUtils.createAvdWizard(getContentPane(), null);
      if (dialog.showAndGet()) {
        AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true);
        initDeviceList(myConfiguration);
      }
    });
  }

  private void initDeviceComboBox() {
    myDevicesComboBox.addActionListener(event -> updateDevicePreview());
  }

  private void initMessage() {
    myMessage.setEditable(false);
    myMessage.setFont(promptLabel.getFont());
    myMessage.setBackground(myPanel.getBackground());
    StyledDocument doc = myMessage.getStyledDocument();
    SimpleAttributeSet center = new SimpleAttributeSet();
    StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
    doc.setParagraphAttributes(0, doc.getLength(), center, false);
    if (myNoMatchingDevice) {
      myMessage.setText(NO_DEVICE_MESSAGE);
      myMessage.setForeground(ERROR_COLOR);
    }
    else {
      myMessage.setText(PROMPT_HELP_TEXT);
      myMessage.setForeground(MESSAGE_COLOR);
    }
  }

  /**
   * Update the preview image withe the selected device
   */
  private void updateDevicePreview() {
    int selectedIndex = myDevicesComboBox.getSelectedIndex();
    if (selectedIndex < 0) {
      selectedIndex = 0;
      myDevicesComboBox.setSelectedIndex(0);
    }

    mySelectedDevice = myMatchingDevices.get(selectedIndex);
    if (mySelectedDevice == null) {
      myDeviceFrame = null;
      return;
    }
    if (myImageCache.containsKey(mySelectedDevice.getId())) {
      myDeviceFrame = myImageCache.get(mySelectedDevice.getId());
      myPreviewPanel.repaint();
    }
    else {
      myDeviceFrame = null;
      myPreviewPanel.repaint();
      final DeviceArtPainter deviceArtPainter = DeviceArtPainter.getInstance();
      if (deviceArtPainter.hasDeviceFrame(mySelectedDevice)) {
        new Thread(() -> {
          try {
            myDeviceFrame = deviceArtPainter.createFrame(
              myImage, mySelectedDevice, myScreenOrientation,
              true /*show effect*/, 1, null);
          }
          catch (IllegalStateException artDescriptorException) {
            LOGGER.warn(artDescriptorException);
            myDeviceFrame = myImage;
          }
          finally {
            UIUtil.invokeLaterIfNeeded(
              () -> myPreviewPanel.repaint());
          }
        }).start();
      }
      else {
        myDeviceFrame = myImage;
      }
    }
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    if (mySelectedDevice != null) {
      setConfigurationDevice(mySelectedDevice);
    }
  }

  private void setConfigurationDevice(@NotNull Device selectedDevice) {
    final State state = selectedDevice.getDefaultState().deepCopy();
    myConfiguration.setDeviceStateName(state.getName());
    myConfiguration.getConfigurationManager().selectDevice(selectedDevice);
  }

  private void createUIComponents() {
    myPreviewPanel = new MyPreviewPanel();
  }

  private class MyPreviewPanel extends JPanel {

    AffineTransform myTransform = new AffineTransform();

    public MyPreviewPanel() {
      setPreferredSize(new Dimension(100, 600));
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (myDeviceFrame == null || mySelectedDevice == null) {
        if (mySelectedDevice == null) {
          drawCenteredString(g, "No device selected", getBounds(), g.getFont());
        }
        else {
          drawCenteredString(g, "Loading...", getBounds(), g.getFont());
        }
        return;
      }
      final Dimension screenSize = mySelectedDevice.getScreenSize(myScreenOrientation);
      if (screenSize != null) {
        int sw = getWidth();
        int sh = getHeight();
        int iw = myDeviceFrame.getWidth();
        int ih = myDeviceFrame.getHeight();
        float scale = Math.min(sw / (float)iw, sh / (float)ih);
        myTransform.setToIdentity();
        myTransform.translate((sw - iw * scale) / 2f, (sh - ih * scale) / 2f);
        myTransform.scale(scale, scale);
        final Graphics2D g2d = (Graphics2D)g;
        final AffineTransform tx = g2d.getTransform();
        g2d.transform(myTransform);
        g2d.drawImage(myDeviceFrame, 0, 0, iw, ih, null);
        g2d.setTransform(tx);
      }
    }

    /**
     * Draw a String centered in the middle of rect.
     *
     * @param g    The Graphics instance.
     * @param text The String to draw.
     * @param rect The Rectangle to center the text in.
     */
    public void drawCenteredString(Graphics g, String text, Rectangle rect, Font font) {
      FontMetrics metrics = g.getFontMetrics(font);
      int x = (rect.width - metrics.stringWidth(text)) / 2;
      // Determine the Y coordinate for the text (note we add the ascent, as in java 2d 0 is top of the screen)
      int y = ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
      g.setFont(font);
      g.drawString(text, x, y);
      g.dispose();
    }
  }
}
