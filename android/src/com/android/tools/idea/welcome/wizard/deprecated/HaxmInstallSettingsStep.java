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
package com.android.tools.idea.welcome.wizard.deprecated;

import static com.android.tools.idea.welcome.install.HaxmKt.UI_UNITS;

import com.android.sdklib.devices.Storage;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.IntProperty;
import com.android.tools.idea.observable.ui.DeprecatedSpinnerValueProperty;
import com.android.tools.idea.observable.ui.SliderValueProperty;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.StartupUiUtil;
import java.awt.Font;
import java.util.Hashtable;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wizard page for setting up Intel® HAXM settings
 * @deprecated use {@link com.android.tools.idea.welcome.wizard.HaxmInstallSettingsStep}
 */
@Deprecated
public final class HaxmInstallSettingsStep extends FirstRunWizardStep {
  private static final int MAJOR_TICKS = 4;
  private static final int MINOR_TICKS = 512;
  // Smallest adjustment user will be able to make with a slider (if the RAM size is small enough)
  private static final int MAX_TICK_RESOLUTION = 32; //Mb
  private static final int MIN_EMULATOR_MEMORY = 512; //Mb

  private final BindingsManager myBindings = new BindingsManager();
  private final IntProperty myEmulatorMemory;
  private final int myRecommendedMemorySize;
  private final ScopedStateStore.Key<Boolean> myKeyCustomInstall;
  private final BoolProperty myInstallHaxm;
  private JBScrollPane myRoot;
  private HyperlinkLabel myIntelHAXMDocumentationButton;
  private JSlider myMemorySlider;
  private JSpinner myMemorySize;
  private JLabel myUnitLabel;
  private JButton myRecommended;

  public HaxmInstallSettingsStep(
    @NotNull ScopedStateStore.Key<Boolean> keyCustomInstall,
    @NotNull BoolProperty installHaxm,
    @NotNull IntProperty emulatorMemory
  ) {
    super("Emulator Settings");
    myKeyCustomInstall = keyCustomInstall;
    myInstallHaxm = installHaxm;
    myUnitLabel.setText(UI_UNITS.toString());
    myEmulatorMemory = emulatorMemory;
    myIntelHAXMDocumentationButton.setHyperlinkText("Intel® HAXM Documentation");
    myIntelHAXMDocumentationButton.setHyperlinkTarget(FirstRunWizardDefaults.HAXM_DOCUMENTATION_URL);
    myRecommendedMemorySize = setupSliderAndSpinner(AvdManagerConnection.getMemorySize(), myMemorySlider, myMemorySize);
    setComponent(myRoot);
    myRecommended.addActionListener(e -> myEmulatorMemory.set(myRecommendedMemorySize));
  }

  @SuppressWarnings("UseOfObsoleteCollectionType")
  private static int setupSliderAndSpinner(long memorySize, @NotNull JSlider slider, @NotNull JSpinner spinner) {
    int recommendedMemorySize = FirstRunWizardDefaults.getRecommendedHaxmMemory(memorySize);
    int maxMemory = Math.max(getMaxMemoryAllocation(memorySize), recommendedMemorySize);

    int ticks = Math.min(maxMemory / MAX_TICK_RESOLUTION, MINOR_TICKS);

    // Empty border is needed to avoid clipping long first and/or last label
    slider.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));

    slider.setMinimum(MIN_EMULATOR_MEMORY);
    slider.setMaximum(maxMemory);
    slider.setMinorTickSpacing(maxMemory / ticks);
    slider.setMajorTickSpacing(maxMemory / MAJOR_TICKS);

    Storage.Unit displayUnit =  getMemoryDisplayUnit(maxMemory * UI_UNITS.getNumberOfBytes());
    Hashtable<Integer, JLabel> labels = new Hashtable<>();
    int labelSpacing = Math.max((maxMemory - MIN_EMULATOR_MEMORY) / MAJOR_TICKS, 1);
    // Avoid overlapping
    int minDistanceBetweenLabels = labelSpacing / 3;
    for (int i = maxMemory; i >= labelSpacing; i -= labelSpacing) {
      if (Math.abs(i - recommendedMemorySize) > minDistanceBetweenLabels && i - MIN_EMULATOR_MEMORY > minDistanceBetweenLabels) {
        labels.put(i, new JLabel(getMemoryLabel(i, displayUnit)));
      }
    }
    if (recommendedMemorySize - MIN_EMULATOR_MEMORY > minDistanceBetweenLabels) {
      labels.put(MIN_EMULATOR_MEMORY, new JLabel(getMemoryLabel(MIN_EMULATOR_MEMORY, UI_UNITS)));
    }
    labels.put(recommendedMemorySize, createRecommendedSizeLabel(recommendedMemorySize, displayUnit));
    slider.setLabelTable(labels);

    spinner.setModel(new SpinnerNumberModel(MIN_EMULATOR_MEMORY, MIN_EMULATOR_MEMORY, maxMemory, maxMemory / ticks));
    return recommendedMemorySize;
  }

  @NotNull
  private static JLabel createRecommendedSizeLabel(int memorySize, Storage.Unit displayUnit) {
    String labelText = String.format("<html><center>%s<br>(Recommended)<center></html>", getMemoryLabel(memorySize, displayUnit));
    final Font boldLabelFont = StartupUiUtil.getLabelFont().deriveFont(Font.BOLD);
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
    final long maxMemory = (memorySize > 4 * GB) ? memorySize - 2 * GB : memorySize / 2;
    return (int)(maxMemory / UI_UNITS.getNumberOfBytes());
  }

  private static Storage.Unit getMemoryDisplayUnit(long memorySizeBytes) {
    Storage.Unit memUnits = Storage.Unit.B;
    for (Storage.Unit unit : Storage.Unit.values()) {
      if (unit.getNumberOfBytes() > memUnits.getNumberOfBytes() && memorySizeBytes / unit.getNumberOfBytes() >= 1) {
        memUnits = unit;
      }
    }
    return memUnits;
  }

  @NotNull
  private static String getMemoryLabel(int memorySize, Storage.Unit displayUnit) {
    long totalMemBytes = memorySize * UI_UNITS.getNumberOfBytes();
    return String.format(Locale.US, "%.1f %s", ((float)totalMemBytes) / displayUnit.getNumberOfBytes(), displayUnit.getDisplayValue());
  }

  @Override
  public boolean isStepVisible() {
    return !SystemInfo.isLinux &&
           Boolean.TRUE.equals(myState.get(myKeyCustomInstall)) &&
           myInstallHaxm.get();
  }

  @Override
  public void init() {
    myBindings.bindTwoWay(new DeprecatedSpinnerValueProperty(myMemorySize), myEmulatorMemory);
    myBindings.bindTwoWay(new SliderValueProperty(myMemorySlider), myEmulatorMemory);
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