/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.*;

/**
 * A help panel that displays help text and error messaging for AVD options.
 */
public class AvdConfigurationOptionHelpPanel extends JPanel {

  private JBLabel myValidationLabel;
  private HaxmAlert myHaxmAlert;
  private JBLabel myTitle;
  private JSeparator mySeparator;
  private JBLabel myDescription;
  private JPanel myContentPanel;
  private JPanel myRoot;
  private JPanel myValidationPanel;

  public AvdConfigurationOptionHelpPanel() {
    mySeparator.setForeground(JBColor.foreground());
    myRoot.setBackground(JBColor.WHITE);
    myTitle.setFont(TITLE_FONT);
    myValidationLabel.setForeground(JBColor.RED);
    add(myRoot);
    Dimension preferred = new Dimension(JBUI.scale(500), -1);
    myTitle.setPreferredSize(preferred);
    myDescription.setPreferredSize(preferred);
    myValidationLabel.setPreferredSize(preferred);
    myValidationPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Error"),
                                                                   BorderFactory.createEmptyBorder(0, 5, 3, 5)));
  }

  public void setSystemImageDescription(SystemImageDescription desc) {
    myHaxmAlert.setSystemImageDescription(desc);
  }

  public void setDescriptionText(@Nullable String descriptionText) {
    if (descriptionText == null || descriptionText.isEmpty()) {
      ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, "NothingSelected");
    }
    else {
      ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, "Info");
      Iterable<String> iterable = Splitter.on('|').split(descriptionText);
      myTitle.setText("<html>" + Iterables.getFirst(iterable, null) + "</html>");
      myDescription.setText("<html>" + Iterables.getLast(iterable, null) + "</html>");
    }
    Window window = SwingUtilities.getWindowAncestor(this);
    if (window != null) {
      window.pack();
    }
  }


  public void setErrorMessage(@NotNull String message) {
    if (message == null || message.isEmpty()) {
      myValidationPanel.setVisible(false);
      return;
    }
    myValidationPanel.setVisible(true);
    myValidationLabel.setText("<html>" + message + "</html>");
    Window window = SwingUtilities.getWindowAncestor(this);
    if (window != null) {
      window.pack();
    }
  }

  private static Map<ScopedStateStore.Key<?>, String> TITLES = ImmutableMap.<ScopedStateStore.Key<?>, String>builder().
    put(RAM_STORAGE_KEY, "Device RAM").
    put(VM_HEAP_STORAGE_KEY, "Virtual Machine Heap").
    put(INTERNAL_STORAGE_KEY, "Internal Flash").
    put(SD_CARD_STORAGE_KEY, "Built-in SD Card Size").
    put(EXISTING_SD_LOCATION, "Location of external SD card image").
    put(SCALE_SELECTION_KEY, "Start-Up Size").
    put(DEFAULT_ORIENTATION_KEY, "Default Orientation").
    put(NETWORK_SPEED_KEY, "Network Speed").
    put(NETWORK_LATENCY_KEY, "Network Latency").
    put(FRONT_CAMERA_KEY, "Front Camera").
    put(BACK_CAMERA_KEY, "Back Camera").
    put(HOST_GPU_MODE_KEY, "Graphics Rendering").
    put(CUSTOM_SKIN_FILE_KEY, "Custom Device Frame").
    put(DISPLAY_NAME_KEY, "AVD Name").
    put(HAS_HARDWARE_KEYBOARD_KEY, "Enable keyboard input").
    put(AVD_ID_KEY, "AVD Id").
    put(DEVICE_FRAME_KEY, "Enable device frame").
    put(CPU_CORES_KEY, "Number of cores").
    build();

  private static Map<ScopedStateStore.Key<?>, String> DESCRIPTIONS = ImmutableMap.<ScopedStateStore.Key<?>, String>builder().
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
                              "See http://d.android.com/tools/help/mksdcard.html for more details. ").
    put(SCALE_SELECTION_KEY, "Enables you to test your application on a screen that uses a resolution or density not supported by the " +
                             "built-in AVD device frames, you can create an AVD that uses a custom resolution by selecting one of the " +
                             "scale values.").
    put(DEFAULT_ORIENTATION_KEY, "Sets the initial orientation of the device. During AVD emulation you can also rotate the device screen. ").
    put(NETWORK_SPEED_KEY, "Sets the initial state of the simulated network transfer rate used by AVD.  " +
                           "The network speed can also be adjusted in the emulator.").
    put(NETWORK_LATENCY_KEY, "Sets the initial state of the simulated network transfer latency used by AVD. " +
                             " Latency is the delay in processing data across the network." +
                             " The latency speed can also be adjusted in the emulator.").
    put(FRONT_CAMERA_KEY, "None - no camera installed for AVD<br>" +
                          "Emulated - use a simulated camera<br>" +
                          "Device -  use host computer webcam or built-in camera").
    put(BACK_CAMERA_KEY, "None - no camera installed for AVD<br>" +
                         "Emulated - use a simulated camera<br>" +
                         "Device -  use host computer webcam or built-in camera").
    put(HOST_GPU_MODE_KEY, "Choose how the graphics should be rendered in the emulator.<br><br>" +
                           "<b>Hardware</b><br>Use your computer's graphics card for faster rendering.<br><br>" +
                           "<b>Software</b><br>Emulate the graphics in software, use this to work around issues with your " +
                           "computer's graphics card.<br><br>" +
                           "<b>Auto</b><br>Let the emulator decide the best option based on knowledge about your graphics card.").
    put(CUSTOM_SKIN_FILE_KEY, "A collection of images and configuration data that indicates how to populate the window. Each skin can have " +
                              "several \"layouts\" (e.g. \"landscape\" and \"portrait\") corresponding to different orientation " +
                              "/ physical configurations of the emulated device.").
    put(DISPLAY_NAME_KEY, "The name of this AVD.").
    put(HAS_HARDWARE_KEYBOARD_KEY, "Enables you to enter text input and interact with the AVD with your hardware computer keyboard " +
                                   "instead of a of the on on-screen software keyboard.").
    put(AVD_ID_KEY, "Identification name used to save an AVD on disk. This AVD name can also be used with Android command line tools.").
    put(DEVICE_FRAME_KEY, "Enable a frame around the Android emulator window that mimics the look of a real " +
                          "Android device. Click on Show Advanced Settings for more options.").
    put(CPU_CORES_KEY, "Select the number of CPU cores for the emulator to use.").
    build();

  /**
   * Get the help text for the given key in the form "Title|Body"
   */
  public String getDescription(ScopedStateStore.Key<?> key) {
    return TITLES.get(key) + "|" + DESCRIPTIONS.get(key);
  }
}
