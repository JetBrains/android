/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run.editor;


import com.android.tools.adtui.ui.ClickableLabel;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JTextPane;

/**
 * The configuration panel for the Android profiler settings.
 */
public class AndroidProfilersPanel {

  private final Project myProject;
  private JPanel myDescription;
  private JTextPane myNativeMemoryRateProfilerDescription;
  // TODO(b/112536124): vertical gap between checkbox and text doesn't toggle the checkbox
  private JCheckBox myAdvancedProfilingCheckBox;
  private ClickableLabel myAdvancedProfilingLabel;
  private JEditorPane myAdvancedProfilingDescription;
  private JCheckBox myStartupProfileCheckBox;
  private ClickableLabel myStartupProfileLabel;
  private LabeledComponent<JBTextField> myNativeMemoryProfilerSampleRate;
  private ComboBox<CpuProfilerConfig> myStartupCpuConfigsComboBox;
  private JTextPane myStartupCpuProfilerDescription;
  private JBRadioButton myCpuRecordingRadio;
  private JBRadioButton myMemoryRecordingRadio;

  public JComponent getComponent() {
    return myDescription;
  }

  public AndroidProfilersPanel(Project project, ProfilerState state) {
    myProject = project;
    addActionListenersToLabels();
    setUpStartupProfiling();
    resetFrom(state);
  }

  void addActionListenersToLabels() {
    myAdvancedProfilingLabel.addActionListener(e -> {
      myAdvancedProfilingCheckBox.requestFocus();
      myAdvancedProfilingCheckBox.setSelected(!myAdvancedProfilingCheckBox.isSelected());
    });
    myStartupProfileLabel.addActionListener(e -> myStartupProfileCheckBox.setSelected(!myStartupProfileCheckBox.isSelected()));
  }

  /**
   * Sets up startup CPU profiling options, there are two options:
   * - myStartupCpuProfileCheckBox - if the checkbox is selected, the next time when the user profiles an application
   * the method trace recording will start automatically with the application launch.
   * - myStartupCpuConfigsComboBox - CPU Configurations that can be used to record a method trace on application launch
   * (e.g Sampled Java, Instrumented Java).
   * The combobox is disabled, if {@code myStartupCpuProfileCheckBox} is unchecked.
   */
  private void setUpStartupProfiling() {
    myStartupProfileCheckBox.addItemListener(e -> {
      myCpuRecordingRadio.setEnabled(myStartupProfileCheckBox.isSelected());
      myMemoryRecordingRadio.setEnabled(myStartupProfileCheckBox.isSelected());
      myStartupCpuConfigsComboBox.setEnabled(myCpuRecordingRadio.isSelected() && myStartupProfileCheckBox.isSelected());
      myStartupProfileCheckBox.setSelected(myStartupProfileCheckBox.isSelected());
    });

    myCpuRecordingRadio.addItemListener(e -> {
      if (myCpuRecordingRadio.isSelected()) {
        myMemoryRecordingRadio.setSelected(false);
        myStartupCpuConfigsComboBox.setEnabled(myCpuRecordingRadio.isSelected());
      }
    });

    myMemoryRecordingRadio.addItemListener(e -> {
      if (myMemoryRecordingRadio.isSelected()) {
        myCpuRecordingRadio.setSelected(false);
        myStartupCpuConfigsComboBox.setEnabled(!myMemoryRecordingRadio.isSelected());
      }
    });

    myStartupCpuConfigsComboBox.setModel(new DefaultComboBoxModel<>(CpuProfilerConfigsState.getInstance(myProject).getConfigs()
                                                                      .toArray(new CpuProfilerConfig[0])));
    myStartupCpuConfigsComboBox.setRenderer(SimpleListCellRenderer.create("", CpuProfilerConfig::getName));
    myStartupCpuConfigsComboBox.setSelectedIndex(0);
  }

  /**
   * Resets the settings panel to the values in the specified {@link ProfilerState}.
   */
  public void resetFrom(ProfilerState state) {
    boolean enabled = myAdvancedProfilingCheckBox.isEnabled();
    myAdvancedProfilingCheckBox.setSelected(enabled && state.ADVANCED_PROFILING_ENABLED);

    myNativeMemoryProfilerSampleRate.getComponent().setText(Integer.toString(state.NATIVE_MEMORY_SAMPLE_RATE_BYTES));
    myNativeMemoryRateProfilerDescription.setBackground(myDescription.getBackground());
    myNativeMemoryRateProfilerDescription.setForeground(UIUtil.getContextHelpForeground());
    myNativeMemoryRateProfilerDescription.setFont(JBFont.small());

    myStartupProfileCheckBox.setSelected(state.STARTUP_PROFILING_ENABLED);
    myCpuRecordingRadio.setSelected(state.STARTUP_CPU_PROFILING_ENABLED);
    myMemoryRecordingRadio.setSelected(state.STARTUP_NATIVE_MEMORY_PROFILING_ENABLED);
    myStartupCpuProfilerDescription.setBackground(myDescription.getBackground());
    myStartupCpuProfilerDescription.setForeground(UIUtil.getContextHelpForeground());
    myStartupCpuProfilerDescription.setFont(JBFont.small());

    String name = state.STARTUP_CPU_PROFILING_CONFIGURATION_NAME;
    CpuProfilerConfig config = CpuProfilerConfigsState.getInstance(myProject).getConfigByName(name);
    if (config != null) {
      myStartupCpuConfigsComboBox.setSelectedItem(config);
    }
  }

  /**
   * Assigns the current UI state to the specified {@link ProfilerState}.
   */
  public void applyTo(ProfilerState state) {
    state.ADVANCED_PROFILING_ENABLED = myAdvancedProfilingCheckBox.isSelected();

    state.STARTUP_CPU_PROFILING_ENABLED = myCpuRecordingRadio.isSelected();
    assert myStartupCpuConfigsComboBox.getSelectedItem() instanceof CpuProfilerConfig;
    state.STARTUP_CPU_PROFILING_CONFIGURATION_NAME = ((CpuProfilerConfig)myStartupCpuConfigsComboBox.getSelectedItem()).getName();
    state.STARTUP_NATIVE_MEMORY_PROFILING_ENABLED = myMemoryRecordingRadio.isSelected();
    state.STARTUP_PROFILING_ENABLED = myStartupProfileCheckBox.isSelected();
    try {
      state.NATIVE_MEMORY_SAMPLE_RATE_BYTES = Math.max(1, Integer.parseInt(myNativeMemoryProfilerSampleRate.getComponent().getText()));
    }
    catch (NumberFormatException ex) {
      state.NATIVE_MEMORY_SAMPLE_RATE_BYTES = ProfilerState.DEFAULT_NATIVE_MEMORY_SAMPLE_RATE_BYTES;
    }
  }

  private void createUIComponents() {
    myAdvancedProfilingDescription =
      SwingHelper.createHtmlViewer(true, null, UIUtil.getPanelBackground(), UIUtil.getContextHelpForeground());
    myAdvancedProfilingDescription.setFont(JBFont.small());
    myAdvancedProfilingDescription.setText(
      "<html>Adds support for network payloads, the event timeline, allocated object count and garbage collection events on devices" +
      " running API level < 26. May slightly increase build time due to compile-time instrumentation. Has no effect on devices running" +
      " API level >= 26. <a href=\"https://developer.android.com/r/studio-ui/profiler/support-for-older-devices\">Learn more</a></html>");
    myAdvancedProfilingDescription.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
  }
}
