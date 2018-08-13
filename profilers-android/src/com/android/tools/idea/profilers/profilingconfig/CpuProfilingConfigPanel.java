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

import com.android.annotations.VisibleForTesting;
import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;

/**
 * The configuration panel for the Android profiler settings.
 */
public class CpuProfilingConfigPanel {

  private static final int MIN_SAMPLING_INTERVAL_US = 100;

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
   * Current configuration that should receive the values set on the panel.
   */
  private ProfilingConfiguration myConfiguration;

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
      return String.format("%d MB", fileSizeLimitInMB);
    }
    return String.format("%.2f GB", fileSizeLimitInMB / 1024.0);
  }

  void setConfiguration(@Nullable ProfilingConfiguration configuration, boolean isDefaultConfiguration) {
    myConfiguration = configuration;
    if (configuration == null) {
      clearFields();
    }
    else {
      myConfigName.setText(configuration.getName());
      myConfigName.setEnabled(true);
      myConfigName.selectAll();
      setAndEnableRadioButtons(configuration);
      myFileSize.setValue(configuration.getProfilingBufferSizeInMb());
      // Starting from Android O, there is no limit on file size, so there is no need to set it.
      setEnabledFileSizeLimit(!myIsDeviceAtLeastO);

      mySamplingInterval.getModel().setValue(configuration.getProfilingSamplingIntervalUs());
    }
    // Default configurations shouldn't be editable.
    if (isDefaultConfiguration) {
      disableFields();
    }
  }

  private void setAndEnableRadioButtons(@NotNull ProfilingConfiguration configuration) {
    myArtSampledButton.setEnabled(true);
    myArtInstrumentedButton.setEnabled(true);
    // There is a flag check before adding this button to the UI, so we can safely set it to enabled here.
    mySimpleperfButton.setEnabled(true);
    if (configuration.getProfilerType() == CpuProfiler.CpuProfilerType.ART) {
      if (configuration.getMode() == CpuProfiler.CpuProfilerMode.SAMPLED) {
        myArtSampledButton.setSelected(true);
        setEnabledSamplingIntervalPanel(true);
      }
      else if (configuration.getMode() == CpuProfiler.CpuProfilerMode.INSTRUMENTED) {
        myArtInstrumentedButton.setSelected(true);
        setEnabledSamplingIntervalPanel(false);
      }
      else {
        getLogger().warn("Invalid trace technology detected.");
      }
    }
    else if (configuration.getProfilerType() == CpuProfiler.CpuProfilerType.SIMPLEPERF) {
      assert configuration.getMode() == CpuProfiler.CpuProfilerMode.SAMPLED;
      mySimpleperfButton.setSelected(true);
      setEnabledSamplingIntervalPanel(true);
    }
    else {
      getLogger().warn("Invalid trace technology detected.");
    }
  }

  private void clearFields() {
    myConfigName.setText("");
    myArtSampledButton.setSelected(false);
    myArtInstrumentedButton.setSelected(false);
    mySimpleperfButton.setSelected(false);
    mySamplingInterval.getModel().setValue(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US);
    myFileSize.setValue(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB);
    myFileSizeLimit.setText("");
  }

  private void disableFields() {
    myConfigName.setEnabled(false);
    myArtSampledButton.setEnabled(false);
    myArtInstrumentedButton.setEnabled(false);
    mySimpleperfButton.setEnabled(false);
    setEnabledSamplingIntervalPanel(false);
    setEnabledFileSizeLimit(false);
  }

  private void createUiComponents() {
    myConfigPanel = new JPanel();
    myConfigPanel.setLayout(new VerticalFlowLayout());

    createConfigNamePanel();
    createTraceTechnologyPanel();
    createSamplingIntervalPanel();
    createFileLimitPanel();

    disableFields();
  }

  private void createConfigNamePanel() {
    JPanel namePanel = new JPanel(new TabularLayout("Fit-,200px", "25px"));
    JLabel nameLabel = new JLabel("Name:");
    nameLabel.setBorder(new EmptyBorder(0, 0, 0, 5));
    namePanel.add(nameLabel, new TabularLayout.Constraint(0, 0));

    myConfigName = new JTextField();
    myConfigName.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (myConfiguration != null) {
          myConfiguration.setName(myConfigName.getText());
        }
      }
    });
    namePanel.add(myConfigName, new TabularLayout.Constraint(0, 1));

    myConfigPanel.add(namePanel);
  }

  private void createTraceTechnologyPanel() {
    JPanel separatorPanel = new JPanel(new TabularLayout("Fit,10px,*"));
    // Fix to the separator not aligning with the text.
    JPanel separatorColumnPanel = new JPanel(new TabularLayout("*", "*,Fit"));
    separatorColumnPanel.add(new JSeparator(), new TabularLayout.Constraint(1, 0));
    separatorPanel.add(new JLabel("Trace technology"), new TabularLayout.Constraint(0, 0));
    separatorPanel.add(separatorColumnPanel, new TabularLayout.Constraint(0, 2));
    myConfigPanel.add(separatorPanel);

    ButtonGroup profilersType = new ButtonGroup();
    myArtSampledButton = new JRadioButton(CpuProfilerConfig.Technology.SAMPLED_JAVA.getName());
    createRadioButtonUi(myArtSampledButton, "Samples Java code using Android Runtime.", TraceTechnology.ART_SAMPLED, profilersType);

    myArtInstrumentedButton = new JRadioButton(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA.getName());
    createRadioButtonUi(myArtInstrumentedButton, "Instruments Java code using Android Runtime.",
                        TraceTechnology.ART_INSTRUMENTED, profilersType);

    mySimpleperfButton = new JRadioButton(CpuProfilerConfig.Technology.SAMPLED_NATIVE.getName());
    createRadioButtonUi(mySimpleperfButton, "<html>Samples native code using simpleperf. " +
                                            "Available for Android 8.0 (API level 26) and higher.</html>",
                        TraceTechnology.SIMPLEPERF, profilersType);
  }

  private void updateConfigurationProfilerAndMode(TraceTechnology technology) {
    switch (technology) {
      case ART_SAMPLED:
        myConfiguration.setProfilerType(CpuProfiler.CpuProfilerType.ART);
        myConfiguration.setMode(CpuProfiler.CpuProfilerMode.SAMPLED);
        setEnabledSamplingIntervalPanel(true);
        break;
      case ART_INSTRUMENTED:
        myConfiguration.setProfilerType(CpuProfiler.CpuProfilerType.ART);
        myConfiguration.setMode(CpuProfiler.CpuProfilerMode.INSTRUMENTED);
        setEnabledSamplingIntervalPanel(false);
        break;
      case SIMPLEPERF:
        myConfiguration.setProfilerType(CpuProfiler.CpuProfilerType.SIMPLEPERF);
        myConfiguration.setMode(CpuProfiler.CpuProfilerMode.SAMPLED);
        setEnabledSamplingIntervalPanel(true);
    }
  }

  private void createRadioButtonUi(JRadioButton button, String description, TraceTechnology technology, ButtonGroup group) {
    button.addActionListener(e -> {
      if (e.getSource() == button) {
        JRadioButton bt = (JRadioButton)e.getSource();
        if (bt.isSelected()) {
          updateConfigurationProfilerAndMode(technology);
        }
      }
    });
    group.add(button);
    myConfigPanel.add(button);
    JLabel descriptionLabel = new JLabel(description);
    descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(12f));
    descriptionLabel.setForeground(ProfilerColors.CPU_RECORDING_CONFIGURATION_DESCRIPTION);
    // TODO: align the description with the radio button text.
    descriptionLabel.setBorder(new EmptyBorder(0, 30, 0, 0));
    myConfigPanel.add(descriptionLabel);
  }

  private void setEnabledSamplingIntervalPanel(boolean isEnabled) {
    mySamplingInterval.setEnabled(isEnabled);
    mySamplingIntervalText.setEnabled(isEnabled);
    mySamplingIntervalUnit.setEnabled(isEnabled);
  }

  private void createSamplingIntervalPanel() {
    JPanel samplingIntervalPanel = new JPanel(new TabularLayout("120px,Fit,Fit-,*", "Fit"));
    samplingIntervalPanel.add(mySamplingIntervalText, new TabularLayout.Constraint(0, 0));
    SpinnerModel model = new SpinnerNumberModel(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US,
                                                MIN_SAMPLING_INTERVAL_US,
                                                MAX_SAMPLING_INTERVAL_US,
                                                SAMPLING_SPINNER_STEP_SIZE);
    mySamplingInterval = new JSpinner(model);
    mySamplingInterval.addChangeListener(e -> {
      JSpinner source = (JSpinner)e.getSource();
      myConfiguration.setProfilingSamplingIntervalUs((Integer)source.getValue());
    });
    samplingIntervalPanel.add(mySamplingInterval, new TabularLayout.Constraint(0, 1));
    mySamplingIntervalUnit.setBorder(new EmptyBorder(0, 5, 0, 0));
    samplingIntervalPanel.add(mySamplingIntervalUnit, new TabularLayout.Constraint(0, 2));
    myConfigPanel.add(samplingIntervalPanel);
  }

  private void setEnabledFileSizeLimit(boolean isEnabled) {
    myFileSize.setEnabled(isEnabled);
    myFileSizeLimit.setEnabled(isEnabled);
    myFileSizeLimitText.setEnabled(isEnabled);
  }

  private void createFileLimitPanel() {
    myFileSize = new JSlider(MIN_FILE_SIZE_LIMIT_MB, myMaxFileSizeLimitMb, ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB);
    myFileSize.setMajorTickSpacing((myMaxFileSizeLimitMb - MIN_FILE_SIZE_LIMIT_MB) / 10);
    myFileSize.setPaintTicks(true);
    myFileSize.addChangeListener(e -> {
      JSlider source = (JSlider)e.getSource();
      myFileSizeLimit.setText(getFileSizeLimitText(source.getValue()));
      myConfiguration.setProfilingBufferSizeInMb(source.getValue());
    });
    myFileSizeLimit = new JLabel(getFileSizeLimitText(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB));
    JPanel fileSizeLimitPanel = new JPanel(new TabularLayout("120px,*,75px", "Fit"));
    fileSizeLimitPanel.add(myFileSizeLimitText, new TabularLayout.Constraint(0, 0));

    fileSizeLimitPanel.add(myFileSize, new TabularLayout.Constraint(0, 1));

    fileSizeLimitPanel.add(myFileSizeLimit, new TabularLayout.Constraint(0, 2));
    myConfigPanel.add(fileSizeLimitPanel);

    myConfigPanel.add(Box.createVerticalStrut(JBUI.scale(6)));

    JLabel description = new JLabel("<html>Maximum size of the output file from recording. On Android 8.0 (API level 26) and higher, " +
                                    "there is no limit on the file size and the value is ignored.</html>");
    description.setForeground(ProfilerColors.CPU_RECORDING_CONFIGURATION_DESCRIPTION);
    description.setFont(description.getFont().deriveFont(12f));
    myConfigPanel.add(description);
  }

  private enum TraceTechnology {
    ART_SAMPLED,
    ART_INSTRUMENTED,
    SIMPLEPERF
  }
}
