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
package com.android.tools.idea.welcome.wizard;

import com.android.sdklib.devices.Storage;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.welcome.install.Haxm;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;

import static com.android.tools.idea.welcome.install.Haxm.UI_UNITS;

/**
 * Wizard page for setting up Intel® HAXM settings for Mac OS X platform
 */
public final class HaxmInstallSettingsStep extends FirstRunWizardStep {
  private static final int MAJOR_TICKS = 4;
  private static final int MINOR_TICKS = 512;
  // Smallest adjustment user will be able to make with a slider (if the RAM size is small enough)
  private static final int MAX_TICK_RESOLUTION = 32; //Mb
  private static final int MIN_EMULATOR_MEMORY = 512; //Mb

  private final ScopedStateStore.Key<Integer> myKeyEmulatorMemory;
  private final int myRecommendedMemorySize;
  private final ScopedStateStore.Key<Boolean> myKeyCustomInstall;
  private final ScopedStateStore.Key<Boolean> myKeyInstallHaxm;
  private JPanel myRoot;
  private com.intellij.ui.HyperlinkLabel myIntelHAXMDocumentationButton;
  private JSlider myMemorySlider;
  private JSpinner myMemorySize;
  private JLabel myUnitLabel;
  private JButton myRecommended;

  public HaxmInstallSettingsStep(ScopedStateStore.Key<Boolean> keyCustomInstall,
                                 ScopedStateStore.Key<Boolean> keyInstallHaxm,
                                 ScopedStateStore.Key<Integer> keyEmulatorMemory) {
    super("Emulator Settings");
    myKeyCustomInstall = keyCustomInstall;
    myKeyInstallHaxm = keyInstallHaxm;
    myUnitLabel.setText(UI_UNITS.toString());
    myKeyEmulatorMemory = keyEmulatorMemory;
    final long memorySize = AvdManagerConnection.getMemorySize();
    myIntelHAXMDocumentationButton.setHyperlinkText("Intel® HAXM Documentation");
    myIntelHAXMDocumentationButton.setHyperlinkTarget(FirstRunWizardDefaults.HAXM_DOCUMENTATION_URL);
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
    int recommendedMemorySize = FirstRunWizardDefaults.getRecommendedHaxmMemory(memorySize);
    int maxMemory = Math.max(getMaxMemoryAllocation(memorySize), recommendedMemorySize);

    int ticks = Math.min(maxMemory / MAX_TICK_RESOLUTION, MINOR_TICKS);

    // Empty border is needed to avoid clipping long first and/or last label
    slider.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));

    slider.setMinimum(MIN_EMULATOR_MEMORY);
    slider.setMaximum(maxMemory);
    slider.setMinorTickSpacing(maxMemory / ticks);
    slider.setMajorTickSpacing(maxMemory / MAJOR_TICKS);

    Hashtable labels = new Hashtable();
    int totalMemory = (int)(memorySize / UI_UNITS.getNumberOfBytes());
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
    return (int)(maxMemory / UI_UNITS.getNumberOfBytes());
  }

  private static String getMemoryLabel(int memorySize) {
    return new Storage(memorySize * UI_UNITS.getNumberOfBytes()).toString();
  }

  @Override
  public boolean isStepVisible() {
    return !SystemInfo.isLinux &&
           Boolean.TRUE.equals(myState.get(myKeyCustomInstall)) &&
           !Boolean.FALSE.equals(myState.get(myKeyInstallHaxm));
  }

  @Override
  public void init() {
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
