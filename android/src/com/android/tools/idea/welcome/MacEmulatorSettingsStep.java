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
import com.android.tools.idea.wizard.ScopedStateStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

  private static final Logger LOG = Logger.getInstance(MacEmulatorSettingsStep.class);
  private final ScopedStateStore.Key<Integer> myKeyEmulatorMemory;
  private final int myRecommendedMemorySize;
  private JPanel myRoot;
  private JButton myIntelHAXMDocumentationButton;
  private JSlider myMemorySlider;
  private JSpinner myMemorySize;
  private JLabel myUnitLabel;
  private JButton myRecommended;

  public MacEmulatorSettingsStep(ScopedStateStore.Key<Integer> keyEmulatorMemory) {
    super("Emulator Settings");
    myUnitLabel.setText(SetupEmulatorPath.UI_UNITS.toString());
    myKeyEmulatorMemory = keyEmulatorMemory;
    final long memorySize = getMemorySize();
    WelcomeUIUtils.makeButtonAHyperlink(myIntelHAXMDocumentationButton, SetupEmulatorPath.HAXM_URL);
    myRecommendedMemorySize = setupSliderAndSpinner(memorySize, myMemorySlider, myMemorySize);
    setComponent(myRoot);
    myRecommended.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myState.put(myKeyEmulatorMemory, myRecommendedMemorySize);
      }
    });
  }

  @SuppressWarnings({"UseOfObsoleteCollectionType", "unchecked"})
  private static int setupSliderAndSpinner(long memorySize, JSlider slider, JSpinner spinner) {
    int recommendedMemorySize = getRecommendedMemoryAllocation(memorySize);
    int maxMemory = Math.max(getMaxMemoryAllocation(memorySize), recommendedMemorySize);

    int ticks = Math.min(maxMemory / MAX_TICK_RESOLUTION, MINOR_TICKS);

    // Empty border is needed to avoid clipping long first and/or last label
    slider.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));

    slider.setMinimum(MIN_EMULATOR_MEMORY);
    slider.setMaximum(maxMemory);
    slider.setMinorTickSpacing(maxMemory / ticks);
    slider.setMajorTickSpacing(maxMemory / MAJOR_TICKS);

    Hashtable labels = new Hashtable();
    int totalMemory = (int)(memorySize / SetupEmulatorPath.UI_UNITS.getNumberOfBytes());
    int labelSpacing = totalMemory / MAJOR_TICKS;
    // Avoid overlapping
    int minDistanceBetweenLabels = labelSpacing / 4;
    for (int i = maxMemory; i >= labelSpacing; i -= labelSpacing) {
      if (Math.abs(i - recommendedMemorySize) > minDistanceBetweenLabels) {
        labels.put(i, new JLabel(getMemoryLabel(i)));
      }
    }
    if (recommendedMemorySize > minDistanceBetweenLabels) {
      labels.put(MIN_EMULATOR_MEMORY, new JLabel(getMemoryLabel(MIN_EMULATOR_MEMORY)));
    }
    labels.put(recommendedMemorySize, createRecommendedSizeLabel(recommendedMemorySize));
    slider.setLabelTable(labels);

    spinner.setModel(new SpinnerNumberModel(MIN_EMULATOR_MEMORY, MIN_EMULATOR_MEMORY, maxMemory, maxMemory / ticks));
    return recommendedMemorySize;
  }

  private static JComponent createRecommendedSizeLabel(int memorySize) {
    String labelText = String.format("<html><center>%s<br>(Recommended)<center></html>", getMemoryLabel(memorySize));
    final Font boldLabelFont = UIUtil.getLabelFont().deriveFont(Font.BOLD);
    // This is the only way as JSlider resets label font.
    return new JLabel(labelText) {
      @Override
      public Font getFont() {
        return boldLabelFont;
      }
    };
  }

  private static int getMaxMemoryAllocation(long memorySize) {
    final long GB = Storage.Unit.GiB.getNumberOfBytes();
    final long maxMemory;
    if (memorySize > 4 * GB) {
      maxMemory = memorySize - 2 * GB;
    }
    else {
      maxMemory = memorySize / 2;
    }
    return (int)(maxMemory / SetupEmulatorPath.UI_UNITS.getNumberOfBytes());
  }

  private static int getRecommendedMemoryAllocation(long memorySize) {
    final long GB = Storage.Unit.GiB.getNumberOfBytes();
    final long defaultMemory;
    if (memorySize > 4 * GB) {
      defaultMemory = 2 * GB;
    }
    else {
      if (memorySize > 2 * GB) {
        defaultMemory = GB;
      }
      else {
        defaultMemory = GB / 2;
      }
    }
    return (int)(defaultMemory / SetupEmulatorPath.UI_UNITS.getNumberOfBytes());
  }

  private static String getMemoryLabel(int memorySize) {
    return new Storage(memorySize * SetupEmulatorPath.UI_UNITS.getNumberOfBytes()).toString();
  }

  private static long getMemorySize() {
    OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
    // This is specific to JDKs derived from Oracle JDK (including OpenJDK and Apple JDK among others).
    // Other then this, there's no standard way of getting memory size
    // without adding 3rd party libraries or using native code.
    try {
      Class<?> oracleSpecificMXBean = Class.forName("com.sun.management.OperatingSystemMXBean");
      Method getPhysicalMemorySizeMethod = oracleSpecificMXBean.getMethod("getTotalPhysicalMemorySize");
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
    myState.put(myKeyEmulatorMemory, myRecommendedMemorySize);

    register(myKeyEmulatorMemory, myMemorySlider);
    register(myKeyEmulatorMemory, myMemorySize, new SpinnerBinding());
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

  private static class SpinnerBinding extends ComponentBinding<Integer, JSpinner> {
    @Override
    public void setValue(@Nullable Integer newValue, @NotNull JSpinner component) {
      component.setValue(newValue);
    }

    @Nullable
    @Override
    public Integer getValue(@NotNull JSpinner component) {
      return (Integer)component.getValue();
    }

    @Override
    public void addChangeListener(@NotNull ChangeListener listener, @NotNull JSpinner component) {
      component.addChangeListener(listener);
    }
  }
}
