/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.devices.Storage;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.Map;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.*;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;

/**
 * A help panel that displays help text and error messaging for AVD options.
 */
public class AvdConfigurationOptionHelpPanel extends JPanel {
  private static final String NO_OPTION_SELECTED = "Nothing Selected";
  private static final int PADDING = 20;
  private String myErrorMessage;
  private String myTitle;
  private String myDescriptionBody;

  public void setDescriptionText(@Nullable String descriptionText) {
    if (descriptionText == null || descriptionText.isEmpty()) {
      myTitle = null;
      myDescriptionBody = null;
      return;
    }
    Iterable<String> iterable = Splitter.on('|').split(descriptionText);
    myTitle = Iterables.getFirst(iterable, null);
    myDescriptionBody = Iterables.getLast(iterable, null);
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    GraphicsUtil.setupAntialiasing(g);
    GraphicsUtil.setupAAPainting(g);
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D)g;
    g2d.setColor(JBColor.background());
    g2d.fillRect(0, 0, getWidth(), getHeight());
    g2d.setColor(JBColor.foreground());
    g2d.setFont(STANDARD_FONT);

    if (myTitle == null) {
      FontMetrics metrics = g2d.getFontMetrics();
      g2d.drawString(NO_OPTION_SELECTED, (getWidth() - metrics.stringWidth(NO_OPTION_SELECTED)) / 2,
                     (getHeight() - metrics.getHeight()) / 2);
    }

    int stringHeight = g2d.getFontMetrics(TITLE_FONT).getHeight();
    int infoSegmentX = PADDING;
    int infoSegmentY = PADDING + stringHeight * 2;
    int maxWidth = getWidth() - 2 * PADDING;
    if (myTitle != null) {
      // Paint the name
      g2d.setFont(TITLE_FONT);
      FontMetrics metrics = g.getFontMetrics(TITLE_FONT);
      g2d.drawString(myTitle, PADDING, PADDING + metrics.getHeight() / 2);
      g2d.drawLine(0, 50, getWidth(), 50);

      // Paint the details.
      g2d.setFont(STANDARD_FONT);
    }
    // Paint our description
    if (myDescriptionBody != null) {
      g2d.setColor(JBColor.foreground());
      for (String line : Splitter.on(CharMatcher.anyOf("\n\r")).omitEmptyStrings().split(myDescriptionBody)) {
        infoSegmentY += drawMultilineString(g2d, line, maxWidth, infoSegmentX, infoSegmentY, false);
      }
    }
    // If there's an error message, paint it
    if (myErrorMessage != null) {
      g2d.setColor(JBColor.RED);
      int height = drawMultilineString(g2d, myErrorMessage, maxWidth, infoSegmentX, infoSegmentY, true);
      drawMultilineString(g2d, myErrorMessage, maxWidth, infoSegmentX, getHeight() - PADDING - height, false);
    }
  }

  /**
   * Returns the height of the text drawn.
   */
  private static int drawMultilineString(@NotNull Graphics2D g2d, @NotNull String fullString, int maxWidth, int startX, int startY,
                                         boolean onlyMeasure) {
    int currentY = startY;
    FontMetrics metrics = g2d.getFontMetrics();
    int stringHeight = metrics.getHeight();
    Iterable<String> parts = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings().split(fullString);
    String currentLine = "";
    for (String part : parts) {
      if (metrics.stringWidth(currentLine + part) > maxWidth) {
        currentY += stringHeight;
        if (!onlyMeasure) {
          g2d.drawString(currentLine, startX, currentY);
        }
        currentLine = "";
      }
      currentLine += part + " ";
    }
    // Flush the remaining buffer
    if (!currentLine.isEmpty()) {
      currentY += stringHeight;
      if (!onlyMeasure) {
        g2d.drawString(currentLine, startX, currentY);
      }
    }
    return currentY - startY;
  }

  public void setErrorMessage(@NotNull String message) {
    myErrorMessage = message;
    repaint();
  }

  private static Map<Key<?>, String> TITLES = ImmutableMap.<Key<?>, String>builder().
      put(RAM_STORAGE_KEY, "Device RAM").
      put(VM_HEAP_STORAGE_KEY, "Virtual Machine Heap").
      put(INTERNAL_STORAGE_KEY, "Internal Flash").
      put(SD_CARD_STORAGE_KEY, "New SD Card Size").
      put(EXISTING_SD_LOCATION, "Location of SD card image").
      put(SCALE_SELECTION_KEY, "Start-Up Size").
      put(DEFAULT_ORIENTATION_KEY, "Default Orientation").
      put(NETWORK_SPEED_KEY, "Network Speed").
      put(NETWORK_LATENCY_KEY, "Network Latency").
      put(FRONT_CAMERA_KEY, "Front Camera").
      put(BACK_CAMERA_KEY, "Back Camera").
      put(USE_HOST_GPU_KEY, "Use Host GPU").
      put(USE_SNAPSHOT_KEY, "Enable Snapshot").
      put(CUSTOM_SKIN_FILE_KEY, "Custom Hardware Skin").
      put(DISPLAY_NAME_KEY, "AVD Name").
      put(HAS_HARDWARE_KEYBOARD_KEY, "Enable keyboard input").
      build();

  private static Map<Key<?>, String> DESCRIPTIONS = ImmutableMap.<Key<?>, String>builder().
    put(RAM_STORAGE_KEY, "The amount of physical RAM on the device.\n" +
                         "1 MB = 1024 KB\n" +
                         "1 GB = 1024 MB").
    put(VM_HEAP_STORAGE_KEY, "The amount of RAM available to Java virtual machine (VM) to allocate to running apps on the device. " +
                             "A larger VM heap allows application to run longer between garbage collection event.").
    put(INTERNAL_STORAGE_KEY, "The amount of non-removable space available to store data on the device.").
    put(SD_CARD_STORAGE_KEY, "The amount of removable space available to store data on the device. " +
                             "We recommend at least 100MB in order to use the camera in the emulator.").
    put(EXISTING_SD_LOCATION, "Choose a file path to an existing SD Card image. Using an external SD Card is useful when sharing " +
                              "SD Card data (pictures, media, files, etc.) between Android Virtual Devices. " +
                              "See http://developer.android.com/tools/help/mksdcard.html for more details. ").
    put(SCALE_SELECTION_KEY, "Enables you to test your application on a screen that uses a resolution or density not supported by the " +
                             "built-in AVD skins, you can create an AVD that uses a custom resolution by selecting one of the scale values.").
    put(DEFAULT_ORIENTATION_KEY, "Sets the initial orientation of the device. During AVD emulation you can also rotate the device screen. ").
    put(NETWORK_SPEED_KEY, "Sets the initial state of the simulated network transfer rate used by AVD.  " +
                           "The network speed can also be adjusted in the emulator.").
    put(NETWORK_LATENCY_KEY, "Sets the initial state of the simulated network transfer latency used by AVD. " +
                             " Latency is the delay in processing data across the network." +
                             " The latency speed can also be adjusted in the emulator.").
    put(FRONT_CAMERA_KEY, "None - no camera installed for AVD\n" +
                          "Emulated - use a simulated camera\n" +
                          "Device -  use host computer webcam or built-in camera").
    put(BACK_CAMERA_KEY, "None - no camera installed for AVD\n" +
                         "Emulated - use a simulated camera\n" +
                         "Device -  use host computer webcam or built-in camera").
    put(USE_HOST_GPU_KEY, "This enables the emulator graphics to run faster by using your computer's graphics card for " +
                          "OpenGL ES graphics rendering.  (Recommended for better emulator experience)").
    put(USE_SNAPSHOT_KEY, "Helps improve emulator re-start performance.  Start the AVD from the AVD manager and check" +
                          " Launch from snapshot and Save to snapshot. This way, when you close the emulator, a snapshot" +
                          " of the AVD state is saved and used to quickly re-launch the AVD next time. " +
                          " Note this will make the emulator slow to close. ").
    put(CUSTOM_SKIN_FILE_KEY, "A collection of images and configuration data that indicates how to populate the window. Each skin can have " +
                              "several \"layouts\" (e.g. \"landscape\" and \"portrait\") corresponding to different orientation " +
                              "/ physical configurations of the emulated device.\n").
    put(DISPLAY_NAME_KEY, "The name of this AVD.").
    put(HAS_HARDWARE_KEYBOARD_KEY, "Enables you to enter text input and interact with the AVD with your hardware computer keyboard " +
                                   "instead of a of the on on-screen software keyboard.\n").
    build();

  /**
   * Get the help text for the given key in the form "Title|Body"
   */
  public String getDescription(Key<?> key) {
    return TITLES.get(key) + "|" + DESCRIPTIONS.get(key);
  }
}
