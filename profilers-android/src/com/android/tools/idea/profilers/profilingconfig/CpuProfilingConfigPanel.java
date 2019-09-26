/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers.profilingconfig;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.android.tools.profilers.cpu.ProfilingTechnology;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.JBUI;
import java.awt.event.ItemEvent;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The configuration panel for the Android profiler settings.
 */
public class CpuProfilingConfigPanel {

  private static final int MIN_SAMPLING_INTERVAL_US = 10;

  private static final int MAX_SAMPLING_INTERVAL_US = 100000;

  private static final int ONE_GB_IN_MB = 1024;

  private static final int SAMPLING_SPINNER_STEP_SIZE = 100;

  @VisibleForTesting
  static final int MIN_FILE_SIZE_LIMIT_MB = 4;

  @VisibleForTesting
  static final String SAMPLING_INTERVAL = "Sampling interval:";

  @VisibleForTesting
  static final String SAMPLING_INTERVAL_UNIT = "microseconds (µs)";

  @VisibleForTesting
  static final String FILE_SIZE_LIMIT = "File size limit:";

  @VisibleForTesting
  static final String DISABLE_LIVE_ALLOCATION = "Suspend Memory Allocation Tracking";

  @VisibleForTesting
  static final String DISABLE_LIVE_ALLOCATION_DESCRIPTION =
    "<html>To minimize performance overhead during CPU recording, suspend memory allocation tracking.</html>";

  @VisibleForTesting
  static final String FILE_SIZE_LIMIT_DESCRIPTION =
    "<html>Maximum recording output file size for Sample/Trace Java Methods." +
    " On Android 8.0 (API level 26) and higher, this value is ignored.</html>";

  /**
   * Max size of the buffer file that contains the output of the recording.
   */
  private int myMaxFileSizeLimitMb;

  /**
   * Main panel containing all the configuration fields.
   */
  private JPanel myConfigPanel;

  /**
   * The configuration identifier name.
   */
  private JTextField myConfigName;

  /**
   * Sampling interval of the configuration.
   * Should be disabled for instrumented configurations.
   */
  private JSpinner mySamplingInterval;

  private final JLabel mySamplingIntervalText = new JLabel(SAMPLING_INTERVAL);

  private final JLabel mySamplingIntervalUnit = new JLabel(SAMPLING_INTERVAL_UNIT);

  /**
   * Controls the size of the buffer file containing the output of the recording.
   * Should be disabled when selected device is O+.
   */
  private JSlider myFileSize;

  private JLabel myFileSizeLimit;

  private final JLabel myFileSizeLimitText = new JLabel(FILE_SIZE_LIMIT);
  private final JLabel myFileSizeLimitDescriptionText = new JLabel(FILE_SIZE_LIMIT_DESCRIPTION);

  private final JCheckBox myDisableLiveAllocation = new JCheckBox(DISABLE_LIVE_ALLOCATION);
  private final JLabel myDisableLiveAllocationDescriptionText = new JLabel(DISABLE_LIVE_ALLOCATION_DESCRIPTION);

  /**
   * Radio button representing Art Sampled configuration.
   */
  private JRadioButton myArtSampledButton;

  /**
   * Radio button representing Art instrumented configuration.
   */
  private JRadioButton myArtInstrumentedButton;

  /**
   * Radio button representing simpleperf configuration.
   */
  private JRadioButton mySimpleperfButton;

  /**
   * Radio button representing system trace (atrace) configuration.
   */
  private JRadioButton myATraceButton;

  private final ButtonGroup myTechnologiesGroup = new ButtonGroup();
  private final JLabel myArtSampledDescriptionText = new JLabel(ProfilingTechnology.ART_SAMPLED.getLongDescription());
  private final JLabel myArtInstrumentedDescriptionText = new JLabel(ProfilingTechnology.ART_INSTRUMENTED.getLongDescription());
  private final JLabel mySimpleperfDescriptionText = new JLabel(ProfilingTechnology.SIMPLEPERF.getLongDescription());
  private final JLabel myATraceDescriptionText = new JLabel(ProfilingTechnology.ATRACE.getLongDescription());

  /**
   * Current configuration that should receive the values set on the panel. Null if no configuration is currently selected.
   */
  @Nullable private ProfilingConfiguration myConfiguration;

  /**
   * Whether device API is at least O.
   */
  private boolean myIsDeviceAtLeastO;

  CpuProfilingConfigPanel(int deviceApiLevel) {
    myIsDeviceAtLeastO = deviceApiLevel >= AndroidVersion.VersionCodes.O;
    // TODO: calculate this value based on device available space
    myMaxFileSizeLimitMb = 4096;
    createUiComponents();
  }

  @VisibleForTesting
  int getMaxFileSizeLimitMb() {
    return myMaxFileSizeLimitMb;
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuProfilingConfigPanel.class);
  }

  JComponent getComponent() {
    return myConfigPanel;
  }

  JComponent getPreferredFocusComponent() {
    return myConfigName;
  }

  /**
   * Gets the file size limit in MB and returns it as a string in MB if the size is less than 1 GB,
   * otherwise it's returned in GB.
   */
  private static String getFileSizeLimitText(int fileSizeLimitInMB) {
    if (fileSizeLimitInMB < ONE_GB_IN_MB) {
      return String.format(Locale.US, "%d MB", fileSizeLimitInMB);
    }
    return String.format(Locale.US, "%.2f GB", fileSizeLimitInMB / 1024.0);
  }

  void setConfiguration(@Nullable ProfilingConfiguration configuration, boolean isDefaultConfiguration) {
    myConfiguration = configuration;
    updateFields();
    // Default configurations shouldn't be editable.
    if (isDefaultConfiguration) {
      disableFields();
    }
  }

  private void updateFields() {
    if (myConfiguration == null) {
      clearFields();
    }
    else {
      myConfigName.setText(myConfiguration.getName());
      myConfigName.setEnabled(true);
      myConfigName.selectAll();
      setEnabledTraceTechnologyPanel(true);
      setRadioButtons(myConfiguration);
      setEnabledFileSizeLimit(!myIsDeviceAtLeastO && myConfiguration.getTraceType().equals(Cpu.CpuTraceType.ART));
      boolean isSamplingEnabled = myConfiguration.getMode() == Cpu.CpuTraceMode.SAMPLED;
      setEnabledSamplingIntervalPanel(isSamplingEnabled);
      myFileSize.setValue(myConfiguration.getProfilingBufferSizeInMb());

      mySamplingInterval.getModel().setValue(myConfiguration.getProfilingSamplingIntervalUs());

      myDisableLiveAllocation.setSelected(myConfiguration.isDisableLiveAllocation());
      setEnabledDisableLiveAllocation(true);
    }

  }

  private void setRadioButtons(@NotNull ProfilingConfiguration configuration) {
    switch (ProfilingTechnology.fromConfig(configuration)) {
      case ART_SAMPLED:
        myArtSampledButton.setSelected(true);
        break;
      case ART_INSTRUMENTED:
        myArtInstrumentedButton.setSelected(true);
        break;
      case SIMPLEPERF:
        mySimpleperfButton.setSelected(true);
        break;
      case ATRACE:
        myATraceButton.setSelected(true);
        break;
      case ART_UNSPECIFIED:
        getLogger().warn("Invalid trace technology detected.");
        break;
    }
  }

  private void clearFields() {
    myConfigName.setText("");
    myTechnologiesGroup.clearSelection();
    mySamplingInterval.getModel().setValue(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US);
    myFileSize.setValue(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB);
    myFileSizeLimit.setText("");
    myDisableLiveAllocation.setSelected(false);
  }

  private void disableFields() {
    myConfigName.setEnabled(false);
    setEnabledTraceTechnologyPanel(false);
    setEnabledSamplingIntervalPanel(false);
    setEnabledFileSizeLimit(false);
    setEnabledDisableLiveAllocation(false);
  }

  private void createUiComponents() {
    myConfigPanel = new JPanel();
    myConfigPanel.setLayout(new VerticalFlowLayout());

    createConfigNamePanel();
    createSectionHeaderPanel("Trace type");
    createTraceTechnologyPanel();
    createSamplingIntervalPanel();
    createFileLimitPanel();
    if (myIsDeviceAtLeastO && StudioFlags.PROFILER_SAMPLE_LIVE_ALLOCATIONS.get()) {
      createSectionHeaderPanel("Performance");
      createDisableLiveAllocationPanel();
    }

    disableFields();
  }

  private void createConfigNamePanel() {
    JPanel namePanel = new JPanel(new TabularLayout("Fit-,200px", "25px"));
    namePanel.setBorder(JBUI.Borders.emptyBottom(4));
    JLabel nameLabel = new JLabel("Name:");
    nameLabel.setBorder(JBUI.Borders.emptyRight(5));
    namePanel.add(nameLabel, new TabularLayout.Constraint(0, 0));

    myConfigName = new JTextField();
    myConfigName.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        if (myConfiguration != null) {
          myConfiguration.setName(myConfigName.getText());
        }
      }
    });
    namePanel.add(myConfigName, new TabularLayout.Constraint(0, 1));

    myConfigPanel.add(namePanel);
  }

  private void createSectionHeaderPanel(String sectionName) {
    JPanel sectionHeaderPanel = new JPanel(new TabularLayout("Fit,10px,*"));
    sectionHeaderPanel.setBorder(JBUI.Borders.emptyTop(12));
    // Fix to the separator not aligning with the text.
    JPanel separatorColumnPanel = new JPanel(new TabularLayout("*", "*,*"));
    separatorColumnPanel.add(new JSeparator(), new TabularLayout.Constraint(1, 0));
    sectionHeaderPanel.add(new JLabel(sectionName), new TabularLayout.Constraint(0, 0));
    sectionHeaderPanel.add(separatorColumnPanel, new TabularLayout.Constraint(0, 2));
    myConfigPanel.add(sectionHeaderPanel);
  }

  private void createTraceTechnologyPanel() {
    myArtSampledButton = new JRadioButton(CpuProfilerConfig.Technology.SAMPLED_JAVA.getName());
    createRadioButtonUi(myArtSampledButton, myArtSampledDescriptionText, ProfilingTechnology.ART_SAMPLED, myTechnologiesGroup);

    myArtInstrumentedButton = new JRadioButton(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA.getName());
    createRadioButtonUi(myArtInstrumentedButton, myArtInstrumentedDescriptionText, ProfilingTechnology.ART_INSTRUMENTED,
                        myTechnologiesGroup);

    mySimpleperfButton = new JRadioButton(CpuProfilerConfig.Technology.SAMPLED_NATIVE.getName());
    createRadioButtonUi(mySimpleperfButton, mySimpleperfDescriptionText, ProfilingTechnology.SIMPLEPERF, myTechnologiesGroup);

    myATraceButton = new JRadioButton(CpuProfilerConfig.Technology.ATRACE.getName());
    createRadioButtonUi(myATraceButton, myATraceDescriptionText, ProfilingTechnology.ATRACE, myTechnologiesGroup);
  }

  private void setEnabledTraceTechnologyPanel(boolean isEnabled) {
    myArtSampledButton.setEnabled(isEnabled);
    myArtInstrumentedButton.setEnabled(isEnabled);
    mySimpleperfButton.setEnabled(isEnabled);
    myATraceButton.setEnabled(isEnabled);
    myArtSampledDescriptionText.setEnabled(isEnabled);
    myArtInstrumentedDescriptionText.setEnabled(isEnabled);
    mySimpleperfDescriptionText.setEnabled(isEnabled);
    myATraceDescriptionText.setEnabled(isEnabled);
  }

  private void createRadioButtonUi(JRadioButton button, JLabel descriptionLabel, ProfilingTechnology technology, ButtonGroup group) {
    button.addActionListener(e -> {
      if (e.getSource() == button) {
        JRadioButton bt = (JRadioButton)e.getSource();
        if (bt.isSelected()) {
          // This is only called when a radio button is selected, so myConfiguration should never be null.
          assert myConfiguration != null;
          myConfiguration.setTraceType(technology.getType());
          myConfiguration.setMode(technology.getMode());
          updateFields();
        }
      }
    });
    group.add(button);

    myConfigPanel.add(button);
    descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(12f));
    descriptionLabel.setForeground(ProfilerColors.CPU_RECORDING_CONFIGURATION_DESCRIPTION);
    // TODO(b/116355169): align the description with the radio button text.
    descriptionLabel.setBorder(JBUI.Borders.emptyLeft(30));
    myConfigPanel.add(descriptionLabel);
  }

  private void setEnabledSamplingIntervalPanel(boolean isEnabled) {
    mySamplingInterval.setEnabled(isEnabled);
    mySamplingIntervalText.setEnabled(isEnabled);
    mySamplingIntervalUnit.setEnabled(isEnabled);
  }

  private void createSamplingIntervalPanel() {
    JPanel samplingIntervalPanel = new JPanel(new TabularLayout("120px,Fit,Fit-,*", "Fit"));
    samplingIntervalPanel.setBorder(JBUI.Borders.emptyTop(12));
    samplingIntervalPanel.add(mySamplingIntervalText, new TabularLayout.Constraint(0, 0));
    SpinnerModel model = new SpinnerNumberModel(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US,
                                                MIN_SAMPLING_INTERVAL_US,
                                                MAX_SAMPLING_INTERVAL_US,
                                                SAMPLING_SPINNER_STEP_SIZE);
    mySamplingInterval = new JSpinner(model);
    mySamplingInterval.addChangeListener(e -> {
      if (myConfiguration != null) {
        JSpinner source = (JSpinner)e.getSource();
        myConfiguration.setProfilingSamplingIntervalUs((Integer)source.getValue());
      }
    });
    samplingIntervalPanel.add(mySamplingInterval, new TabularLayout.Constraint(0, 1));
    mySamplingIntervalUnit.setBorder(JBUI.Borders.emptyLeft(5));
    samplingIntervalPanel.add(mySamplingIntervalUnit, new TabularLayout.Constraint(0, 2));
    myConfigPanel.add(samplingIntervalPanel);
  }

  private void setEnabledFileSizeLimit(boolean isEnabled) {
    myFileSize.setEnabled(isEnabled);
    myFileSizeLimit.setEnabled(isEnabled);
    myFileSizeLimitText.setEnabled(isEnabled);
    myFileSizeLimitDescriptionText.setEnabled(isEnabled);
  }

  private void createFileLimitPanel() {
    myFileSize = new JSlider(MIN_FILE_SIZE_LIMIT_MB, myMaxFileSizeLimitMb, ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB);
    myFileSize.setMajorTickSpacing((myMaxFileSizeLimitMb - MIN_FILE_SIZE_LIMIT_MB) / 10);
    myFileSize.setPaintTicks(true);
    myFileSize.addChangeListener(e -> {
      if (myConfiguration != null) {
        JSlider source = (JSlider)e.getSource();
        myFileSizeLimit.setText(getFileSizeLimitText(source.getValue()));
        myConfiguration.setProfilingBufferSizeInMb(source.getValue());
      }
    });
    myFileSizeLimit = new JLabel(getFileSizeLimitText(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB));
    JPanel fileSizeLimitPanel = new JPanel(new TabularLayout("120px,*,75px", "Fit"));
    fileSizeLimitPanel.setBorder(JBUI.Borders.emptyTop(8));
    fileSizeLimitPanel.add(myFileSizeLimitText, new TabularLayout.Constraint(0, 0));

    fileSizeLimitPanel.add(myFileSize, new TabularLayout.Constraint(0, 1));

    fileSizeLimitPanel.add(myFileSizeLimit, new TabularLayout.Constraint(0, 2));
    myConfigPanel.add(fileSizeLimitPanel);

    myConfigPanel.add(Box.createVerticalStrut(JBUI.scale(6)));

    myFileSizeLimitDescriptionText.setBorder(JBUI.Borders.emptyTop(8));
    myFileSizeLimitDescriptionText.setForeground(ProfilerColors.CPU_RECORDING_CONFIGURATION_DESCRIPTION);
    myFileSizeLimitDescriptionText.setFont(myFileSizeLimitDescriptionText.getFont().deriveFont(12f));
    myConfigPanel.add(myFileSizeLimitDescriptionText);
  }

  private void createDisableLiveAllocationPanel() {
    myDisableLiveAllocation.addItemListener(e -> {
      if (myConfiguration != null) {
        myConfiguration.setDisableLiveAllocation(e.getStateChange() == ItemEvent.SELECTED);
      }
    });

    myDisableLiveAllocation.setBorder(JBUI.Borders.emptyTop(8));
    myConfigPanel.add(myDisableLiveAllocation);
    // TODO(b/116355169): align the description with the checkbox button text.
    myDisableLiveAllocationDescriptionText.setBorder(JBUI.Borders.empty(4, 30, 0, 0));
    myDisableLiveAllocationDescriptionText.setForeground(ProfilerColors.CPU_RECORDING_CONFIGURATION_DESCRIPTION);
    myDisableLiveAllocationDescriptionText.setFont(myFileSizeLimitDescriptionText.getFont().deriveFont(12f));
    myConfigPanel.add(myDisableLiveAllocationDescriptionText);
  }

  private void setEnabledDisableLiveAllocation(boolean isEnabled) {
    myDisableLiveAllocation.setEnabled(isEnabled);
    myDisableLiveAllocationDescriptionText.setEnabled(isEnabled);
  }
}
