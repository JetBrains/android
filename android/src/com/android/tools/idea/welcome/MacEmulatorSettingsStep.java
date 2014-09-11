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
package com.android.tools.idea.welcome;

import com.android.sdklib.devices.Storage;
import com.android.sdklib.devices.Storage.Unit;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Hashtable;

/**
 * Wizard page for setting up Intel® HAXM settings for Mac OS X platform
 */
public final class MacEmulatorSettingsStep extends FirstRunWizardStep {
  private static final int MAJOR_TICKS = 4;
  private static final int MINOR_TICKS = 512;
  // Smallest adjustment user will be able to make with a slider (if the RAM size is small enough)
  private static final int MAX_TICK_RESOLUTION = 32; //Mb
  private static final int MIN_EMULATOR_MEMORY = 512; //Mb

  // In UI we cannot use longs, so we need to pick a unit other then byte
  private static final long UI_UNIT_BYTES = Unit.MiB.getNumberOfBytes();

  private static final Logger LOG = Logger.getInstance(MacEmulatorSettingsStep.class);

  private JPanel myRoot;
  private JButton myIntelHAXMDocumentationButton;
  private JSlider myMemorySlider;
  private JLabel mySelectedRAM;

  @SuppressWarnings({"UseOfObsoleteCollectionType", "unchecked"})
  public MacEmulatorSettingsStep() {
    super("Emulator Settings");
    int memorySize = (int)(getMemorySize() / UI_UNIT_BYTES); // Mbs
    int ticks = Math.min(memorySize / MAX_TICK_RESOLUTION, MINOR_TICKS);
    WelcomeUIUtils.makeButtonAHyperlink(myIntelHAXMDocumentationButton);

    // Empty border is needed to avoid clipping long first and/or last label
    myMemorySlider.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));

    myMemorySlider.setMinimum(MIN_EMULATOR_MEMORY);
    myMemorySlider.setMaximum(memorySize);
    myMemorySlider.setMinorTickSpacing(memorySize / ticks);
    myMemorySlider.setMajorTickSpacing(memorySize / MAJOR_TICKS);

    myMemorySlider.setValue(Math.max(MIN_EMULATOR_MEMORY, memorySize / 2));

    mySelectedRAM.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    mySelectedRAM.setText(getMemoryLabel(myMemorySlider.getValue()));

    Hashtable labels = new Hashtable();
    labels.put(MIN_EMULATOR_MEMORY, new JLabel(getMemoryLabel(MIN_EMULATOR_MEMORY)));
    for (int i = 0; i <= memorySize; i += memorySize / MAJOR_TICKS) {
      labels.put(i, new JLabel(getMemoryLabel(i)));
    }
    myMemorySlider.setLabelTable(labels);

    setComponent(myRoot);
  }

  private static String getMemoryLabel(int memorySize) {
    return new Storage(memorySize * UI_UNIT_BYTES).toString();
  }

  private static long getMemorySize() {
    OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
    // This is specific to JDKs derived from Oracle JDK (including OpenJDK and Apple JDK among others).
    // Other then this, there's no standard way of getting memory size
    // without adding 3rd party libraries or using native code.
    try {
      Class<?> oracleSpecifixMXBean = Class.forName("com.sun.management.OperatingSystemMXBean");
      Method getPhysicalMemorySizeMethod = oracleSpecifixMXBean.getMethod("getTotalPhysicalMemorySize");
      Object result = getPhysicalMemorySizeMethod.invoke(osMXBean);
      if (result instanceof Number) {
        return ((Number)result).longValue();
      }
    }
    catch (ClassNotFoundException e) {
      // Unsupported JDK
    }
    catch (NoSuchMethodException e) {
      // Unsupported JDK
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    // Maximum memory allocatable to emulator - 32G. Only used if non-Oracle JRE.
    return 32L * (1 << 30);
  }

  @Override
  public boolean isStepVisible() {
    return SystemInfo.isMac;
  }

  @Override
  public void init() {

  }

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myMemorySlider;
  }
}
