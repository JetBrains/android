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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
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

  /**
   * Size of the buffer file containing the output of the recording.
   * Should be disabled when selected device is O+.
   */
  private JTextField myFileSizeLimit;

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

  void setConfiguration(@Nullable ProfilingConfiguration configuration, boolean isDefaultConfiguration) {
    myConfiguration = configuration;
    if (configuration == null) {
      clearFields();
    }
    else {
      myConfigName.setText(configuration.getName());
      myConfigName.setEnabled(true);

      setAndEnableRadioButtons(configuration);

      myFileSizeLimit.setText(String.valueOf(configuration.getProfilingBufferSizeInMb()));
      // Starting from Android O, there is no limit on file size, so there is no need to set it.
      myFileSizeLimit.setEnabled(!myIsDeviceAtLeastO);

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
    if (configuration.getProfiler() == CpuProfiler.CpuProfilingAppStartRequest.Profiler.ART) {
      if (configuration.getMode() == CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED) {
        myArtSampledButton.setSelected(true);
        mySamplingInterval.setEnabled(true);
      }
      else if (configuration.getMode() == CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED) {
        myArtInstrumentedButton.setSelected(true);
        mySamplingInterval.setEnabled(false);
      }
      else {
        getLogger().warn("Invalid trace technology detected.");
      }
    }
    else if (configuration.getProfiler() == CpuProfiler.CpuProfilingAppStartRequest.Profiler.SIMPLE_PERF) {
      assert configuration.getMode() == CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED;
      mySimpleperfButton.setSelected(true);
      mySamplingInterval.setEnabled(true);
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
    mySamplingInterval.setEnabled(false);
    myFileSizeLimit.setEnabled(false);
  }

  private void createUiComponents() {
    // TODO: adjust style of everything
    myConfigPanel = new JPanel();
    myConfigPanel.setLayout(new BoxLayout(myConfigPanel, BoxLayout.Y_AXIS));

    createConfigNamePanel();
    addSeparator();
    createTraceTechnologyPanel();
    addSeparator();
    createSamplingIntervalPanel();
    addSeparator();
    createFileLimitPanel();

    JPanel bottomFiller = new JPanel();
    bottomFiller.setPreferredSize(new Dimension(10, 1000));
    myConfigPanel.add(bottomFiller);

    disableFields();
  }

  private void addSeparator() {
    JSeparator separator = new JSeparator();
    Dimension dim = new Dimension(20, 20);
    separator.setPreferredSize(dim);
    separator.setMinimumSize(dim);
    myConfigPanel.add(separator);
  }

  private void createConfigNamePanel() {
    JPanel namePanel = new JPanel();
    namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.X_AXIS));

    namePanel.add(new JLabel("Name:"));
    namePanel.add(Box.createRigidArea(new Dimension(5, 0)));

    myConfigName = new JTextField();
    myConfigName.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (myConfiguration != null) {
          myConfiguration.setName(myConfigName.getText());
        }
      }
    });
    Dimension nameMaxSize = myConfigName.getMaximumSize();
    nameMaxSize.height = (int)myConfigName.getPreferredSize().getHeight();
    myConfigName.setMaximumSize(nameMaxSize);
    namePanel.add(myConfigName);

    myConfigPanel.add(namePanel);
  }

  private void createTraceTechnologyPanel() {
    JPanel traceTechnologyPanel = new JPanel();
    traceTechnologyPanel.setLayout(new BoxLayout(traceTechnologyPanel, BoxLayout.X_AXIS));
    traceTechnologyPanel.add(new JLabel("Trace technology"));
    traceTechnologyPanel.add(Box.createHorizontalGlue());
    myConfigPanel.add(traceTechnologyPanel);

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
        myConfiguration.setProfiler(CpuProfiler.CpuProfilingAppStartRequest.Profiler.ART);
        myConfiguration.setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED);
        mySamplingInterval.setEnabled(true);
        break;
      case ART_INSTRUMENTED:
        myConfiguration.setProfiler(CpuProfiler.CpuProfilingAppStartRequest.Profiler.ART);
        myConfiguration.setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED);
        mySamplingInterval.setEnabled(false);
        break;
      case SIMPLEPERF:
        myConfiguration.setProfiler(CpuProfiler.CpuProfilingAppStartRequest.Profiler.SIMPLE_PERF);
        myConfiguration.setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED);
        mySamplingInterval.setEnabled(true);
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

    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
    buttonPanel.add(Box.createRigidArea(new Dimension(5, 0)));
    buttonPanel.add(button);
    buttonPanel.add(Box.createHorizontalGlue());
    myConfigPanel.add(buttonPanel);

    JPanel descriptionPanel = new JPanel();
    descriptionPanel.setLayout(new BoxLayout(descriptionPanel, BoxLayout.X_AXIS));
    // TODO: align the description with the radio button text.
    descriptionPanel.add(Box.createRigidArea(new Dimension(35, 0)));
    JLabel descriptionLabel = new JLabel(description);
    descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(12f));
    descriptionPanel.add(descriptionLabel);
    descriptionPanel.add(Box.createHorizontalGlue());
    myConfigPanel.add(descriptionPanel);
  }

  private void createSamplingIntervalPanel() {
    JPanel samplingIntervalPanel = new JPanel();
    samplingIntervalPanel.setLayout(new BoxLayout(samplingIntervalPanel, BoxLayout.X_AXIS));
    samplingIntervalPanel.add(new JLabel("Sampling interval:"));
    samplingIntervalPanel.add(Box.createRigidArea(new Dimension(5, 0)));

    mySamplingInterval = createNumberTextField(MIN_SAMPLING_INTERVAL_US, MAX_SAMPLING_INTERVAL_US,
                                               ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US,
                                               value -> myConfiguration.setProfilingSamplingIntervalUs(value),
                                               String.format("The sampling interval should be a value between %d and %d microseconds",
                                                             MIN_SAMPLING_INTERVAL_US, MAX_SAMPLING_INTERVAL_US));
    Dimension samplingMaxSize = mySamplingInterval.getMaximumSize();
    samplingMaxSize.height = (int)mySamplingInterval.getPreferredSize().getHeight();
    samplingMaxSize.width = 75;
    mySamplingInterval.setMaximumSize(samplingMaxSize);
    mySamplingInterval.setPreferredSize(samplingMaxSize);
    samplingIntervalPanel.add(mySamplingInterval);

    samplingIntervalPanel.add(Box.createRigidArea(new Dimension(5, 0)));
    samplingIntervalPanel.add(new JLabel("microseconds (µs)"));
    samplingIntervalPanel.add(Box.createHorizontalGlue());

    myConfigPanel.add(samplingIntervalPanel);
  }

  private void createFileLimitPanel() {
    JPanel fileSizeLimitPanel = new JPanel();
    fileSizeLimitPanel.setLayout(new BoxLayout(fileSizeLimitPanel, BoxLayout.X_AXIS));
    fileSizeLimitPanel.add(new JLabel("File size limit:"));
    fileSizeLimitPanel.add(Box.createRigidArea(new Dimension(5, 0)));

    myFileSizeLimit = createNumberTextField(MIN_FILE_SIZE_LIMIT_MB, myMaxFileSizeLimitMb, ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB,
                                            value -> myConfiguration.setProfilingBufferSizeInMb(value),
                                            String.format("The file buffer maximum size should be a value between %d and %d MB",
                                                          MIN_FILE_SIZE_LIMIT_MB, myMaxFileSizeLimitMb));
    Dimension fileMaxSize = myFileSizeLimit.getMaximumSize();
    fileMaxSize.height = (int)myFileSizeLimit.getPreferredSize().getHeight();
    fileMaxSize.width = 75;
    myFileSizeLimit.setMaximumSize(fileMaxSize);
    myFileSizeLimit.setPreferredSize(fileMaxSize);
    fileSizeLimitPanel.add(myFileSizeLimit);

    fileSizeLimitPanel.add(Box.createRigidArea(new Dimension(5, 0)));
    fileSizeLimitPanel.add(new JLabel("MB"));
    fileSizeLimitPanel.add(Box.createHorizontalGlue());

    myConfigPanel.add(fileSizeLimitPanel);

    myConfigPanel.add(Box.createRigidArea(new Dimension(0, 5)));

    JPanel descriptionPanel = new JPanel();
    descriptionPanel.setLayout(new BoxLayout(descriptionPanel, BoxLayout.X_AXIS));
    JLabel description = new JLabel("<html>Maximum size of the output file from recording. On Android O or greater, " +
                                    "there is no limit on the file size and the value is ignored.</html>");
    description.setFont(description.getFont().deriveFont(12f));
    descriptionPanel.add(description);
    descriptionPanel.add(Box.createHorizontalGlue());

    myConfigPanel.add(descriptionPanel);
  }

  private enum TraceTechnology {
    ART_SAMPLED,
    ART_INSTRUMENTED,
    SIMPLEPERF
  }
}
