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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.function.Consumer;

/**
 * The configuration panel for the Android profiler settings.
 */
public class CpuProfilingConfigPanel {

  private static final int MIN_SAMPLING_INTERVAL_US = 100;

  private static final int MAX_SAMPLING_INTERVAL_US = 100000;

  private static final int MIN_FILE_SIZE_LIMIT_MB = 4;

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
  private JTextField mySamplingInterval;

  private final JLabel mySamplingIntervalText = new JLabel("Sampling interval:");

  private final JLabel mySamplingIntervalUnit = new JLabel("microseconds (µs)");

  /**
   * Size of the buffer file containing the output of the recording.
   * Should be disabled when selected device is O+.
   */
  private JTextField myFileSizeLimit;

  private final JLabel myFileSizeLimitText = new JLabel("File size limit:");

  private final JLabel myFileSizeLimitUnit = new JLabel("MB");

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

  CpuProfilingConfigPanel(boolean isDeviceAtLeastO) {
    myIsDeviceAtLeastO = isDeviceAtLeastO;
    // TODO: calculate this value based on device available space
    myMaxFileSizeLimitMb = 4096;
    createUiComponents();
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuProfilingConfigPanel.class);
  }

  /**
   * Creates a text field that validate its content when focus is lost.
   * The content should be an integer between min and max (inclusive).
   * A given callback is called if the input value is a valid integer between min and max.
   * If the content is not a valid number, a default value should replace it.
   * If the input text is valid number outside the range [min, max], it should be replaced by the closest valid value.
   */
  private static JTextField createNumberTextField(int min, int max, int defaultValue, Consumer<Integer> callback, String tooltip) {
    JTextField textField = new JTextField();
    textField.setHorizontalAlignment(JTextField.RIGHT);
    textField.setToolTipText(tooltip);
    textField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        super.focusLost(e);
        try {
          int value = Integer.parseInt(textField.getText());
          value = Math.max(Math.min(max, value), min);
          textField.setText(String.valueOf(value));
          callback.accept(value);
        }
        catch (Exception ex) {
          textField.setText(String.valueOf(defaultValue));
        }
      }
    });
    return textField;
  }

  JComponent getComponent() {
    return myConfigPanel;
  }

  JComponent getPreferredFocusComponent() {
    return myConfigName;
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

      myFileSizeLimit.setText(String.valueOf(configuration.getProfilingBufferSizeInMb()));
      // Starting from Android O, there is no limit on file size, so there is no need to set it.
      setEnabledFileSizeLimit(!myIsDeviceAtLeastO);

      mySamplingInterval.setText(String.valueOf(configuration.getProfilingSamplingIntervalUs()));
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
      if (configuration.getMode() == CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED) {
        myArtSampledButton.setSelected(true);
        setEnabledSamplingIntervalPanel(true);
      }
      else if (configuration.getMode() == CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED) {
        myArtInstrumentedButton.setSelected(true);
        setEnabledSamplingIntervalPanel(false);
      }
      else {
        getLogger().warn("Invalid trace technology detected.");
      }
    }
    else if (configuration.getProfilerType() == CpuProfiler.CpuProfilerType.SIMPLE_PERF) {
      assert configuration.getMode() == CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED;
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
    mySamplingInterval.setText("");
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
    addSeparator();
    createTraceTechnologyPanel();
    addSeparator();
    createSamplingIntervalPanel();
    addSeparator();
    createFileLimitPanel();

    disableFields();
  }

  private void addSeparator() {
    JPanel separatorPanel = new JPanel(new TabularLayout("*", "10px"));
    separatorPanel.add(new JSeparator(), new TabularLayout.Constraint(0, 0));
    myConfigPanel.add(separatorPanel);
  }

  private void createConfigNamePanel() {
    JPanel namePanel = new JPanel(new TabularLayout("Fit,200px", "25px"));
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
    myConfigPanel.add(new JLabel("Trace technology"));

    ButtonGroup profilersType = new ButtonGroup();
    myArtSampledButton = new JRadioButton(ProfilingConfiguration.ART_SAMPLED);
    createRadioButtonUi(myArtSampledButton, "Samples Java code using Android Runtime.", TraceTechnology.ART_SAMPLED, profilersType);

    mySimpleperfButton = new JRadioButton(ProfilingConfiguration.SIMPLEPERF);
    if (StudioFlags.PROFILER_USE_SIMPLEPERF.get()) {
      createRadioButtonUi(mySimpleperfButton, "<html>Samples Java and native code using simpleperf. " +
                                              "Available for Android O and greater.</html>",
                          TraceTechnology.SIMPLEPERF, profilersType);
    }

    myArtInstrumentedButton = new JRadioButton(ProfilingConfiguration.ART_INSTRUMENTED);
    createRadioButtonUi(myArtInstrumentedButton, "Instruments Java code using Android Runtime.",
                        TraceTechnology.ART_INSTRUMENTED, profilersType);
  }

  private void updateConfigurationProfilerAndMode(TraceTechnology technology) {
    switch (technology) {
      case ART_SAMPLED:
        myConfiguration.setProfilerType(CpuProfiler.CpuProfilerType.ART);
        myConfiguration.setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED);
        setEnabledSamplingIntervalPanel(true);
        break;
      case ART_INSTRUMENTED:
        myConfiguration.setProfilerType(CpuProfiler.CpuProfilerType.ART);
        myConfiguration.setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED);
        setEnabledSamplingIntervalPanel(false);
        break;
      case SIMPLEPERF:
        myConfiguration.setProfilerType(CpuProfiler.CpuProfilerType.SIMPLE_PERF);
        myConfiguration.setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED);
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
    // TODO: align the description with the radio button text.
    descriptionLabel.setBorder(new EmptyBorder(0, 30, 0, 0));
    myConfigPanel.add(descriptionLabel);
  }

  private void setEnabledSamplingIntervalPanel(boolean isEnabled) {
    mySamplingInterval.setEnabled(isEnabled);
    mySamplingIntervalText.setEnabled(isEnabled);
    mySamplingIntervalUnit.setEnabled(isEnabled);
  }

  /**
   * Layout used by sampling interval and file size related components.
   * The same layout is used so their components keep aligned.
   */
  private static TabularLayout getSamplingIntervalFileSizeLayout() {
    return new TabularLayout("120px,75px,Fit,*", "25px");
  }

  private void createSamplingIntervalPanel() {
    JPanel samplingIntervalPanel = new JPanel(getSamplingIntervalFileSizeLayout());
    samplingIntervalPanel.add(mySamplingIntervalText, new TabularLayout.Constraint(0, 0));
    mySamplingInterval = createNumberTextField(MIN_SAMPLING_INTERVAL_US, MAX_SAMPLING_INTERVAL_US,
                                               ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US,
                                               value -> myConfiguration.setProfilingSamplingIntervalUs(value),
                                               String.format("The sampling interval should be a value between %d and %d microseconds",
                                                             MIN_SAMPLING_INTERVAL_US, MAX_SAMPLING_INTERVAL_US));
    samplingIntervalPanel.add(mySamplingInterval, new TabularLayout.Constraint(0, 1));
    mySamplingIntervalUnit.setBorder(new EmptyBorder(0, 5, 0, 0));
    samplingIntervalPanel.add(mySamplingIntervalUnit, new TabularLayout.Constraint(0, 2));
    myConfigPanel.add(samplingIntervalPanel);
  }

  private void setEnabledFileSizeLimit(boolean isEnabled) {
    myFileSizeLimit.setEnabled(isEnabled);
    myFileSizeLimitText.setEnabled(isEnabled);
    myFileSizeLimitUnit.setEnabled(isEnabled);
  }

  private void createFileLimitPanel() {
    JPanel fileSizeLimitPanel = new JPanel(getSamplingIntervalFileSizeLayout());
    fileSizeLimitPanel.add(myFileSizeLimitText, new TabularLayout.Constraint(0, 0));
    myFileSizeLimit = createNumberTextField(MIN_FILE_SIZE_LIMIT_MB, myMaxFileSizeLimitMb, ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB,
                                            value -> myConfiguration.setProfilingBufferSizeInMb(value),
                                            String.format("The file buffer maximum size should be a value between %d and %d MB",
                                                          MIN_FILE_SIZE_LIMIT_MB, myMaxFileSizeLimitMb));
    fileSizeLimitPanel.add(myFileSizeLimit, new TabularLayout.Constraint(0, 1));

    myFileSizeLimitUnit.setBorder(new EmptyBorder(0, 5, 0, 0));
    fileSizeLimitPanel.add(myFileSizeLimitUnit, new TabularLayout.Constraint(0, 2));
    myConfigPanel.add(fileSizeLimitPanel);

    JLabel description = new JLabel("<html>Maximum size of the output file from recording. On Android O or greater, " +
                                    "there is no limit on the file size and the value is ignored.</html>");
    description.setFont(description.getFont().deriveFont(12f));
    myConfigPanel.add(description);
  }

  private enum TraceTechnology {
    ART_SAMPLED,
    ART_INSTRUMENTED,
    SIMPLEPERF
  }
}
