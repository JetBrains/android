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
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.Locale;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;

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
    setupUI();
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

  private void setupUI() {
    createUIComponents();
    myDescription = new JPanel();
    myDescription.setLayout(new GridLayoutManager(7, 4, new Insets(10, 10, 10, 10), -1, -1));
    myStartupProfileCheckBox = new JCheckBox();
    myStartupProfileCheckBox.setHorizontalAlignment(4);
    myStartupProfileCheckBox.setHorizontalTextPosition(4);
    myStartupProfileCheckBox.setIconTextGap(0);
    myStartupProfileCheckBox.setMargin(new Insets(1, 0, 0, 0));
    myStartupProfileCheckBox.setText("");
    myDescription.add(myStartupProfileCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, new Dimension(8, 23), null, 0, false));
    final Spacer spacer1 = new Spacer();
    myDescription.add(spacer1, new GridConstraints(6, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                   GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myAdvancedProfilingDescription.setEditable(false);
    Font myAdvancedProfilingDescriptionFont = getFont(null, -1, -1, myAdvancedProfilingDescription.getFont());
    if (myAdvancedProfilingDescriptionFont != null) myAdvancedProfilingDescription.setFont(myAdvancedProfilingDescriptionFont);
    myDescription.add(myAdvancedProfilingDescription,
                      new GridConstraints(6, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                          GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                          new Dimension(150, 50), null, 0, false));
    myStartupProfileLabel = new ClickableLabel();
    myStartupProfileLabel.setMargin(new Insets(2, 14, 0, 14));
    myStartupProfileLabel.setText("Start this recording on startup:");
    myDescription.add(myStartupProfileLabel, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                                                   GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                 null, null, null, 0, false));
    myNativeMemoryRateProfilerDescription = new JTextPane();
    myNativeMemoryRateProfilerDescription.setAutoscrolls(false);
    myNativeMemoryRateProfilerDescription.setDoubleBuffered(false);
    myNativeMemoryRateProfilerDescription.setEditable(false);
    myNativeMemoryRateProfilerDescription.setEnabled(true);
    Font myNativeMemoryRateProfilerDescriptionFont = getFont(null, -1, -1, myNativeMemoryRateProfilerDescription.getFont());
    if (myNativeMemoryRateProfilerDescriptionFont != null) {
      myNativeMemoryRateProfilerDescription.setFont(myNativeMemoryRateProfilerDescriptionFont);
    }
    myNativeMemoryRateProfilerDescription.setMargin(new Insets(0, 3, 0, 3));
    myNativeMemoryRateProfilerDescription.setText(
      "Native memory sampling rate. This value when set will remain for both startup and non-startup recordings.");
    myDescription.add(myNativeMemoryRateProfilerDescription,
                      new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, 1, 1, null, null, null, 0,
                                          false));
    myAdvancedProfilingCheckBox = new JCheckBox();
    myAdvancedProfilingCheckBox.setAlignmentX(1.0f);
    myAdvancedProfilingCheckBox.setHorizontalAlignment(4);
    myAdvancedProfilingCheckBox.setHorizontalTextPosition(4);
    myAdvancedProfilingCheckBox.setIconTextGap(0);
    myAdvancedProfilingCheckBox.setMargin(new Insets(1, 0, 0, 0));
    myAdvancedProfilingCheckBox.setText("");
    myDescription.add(myAdvancedProfilingCheckBox, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                       null, new Dimension(8, 23), null, 0, false));
    myAdvancedProfilingLabel = new ClickableLabel();
    myAdvancedProfilingLabel.setText("Enable additional support for older devices (API level < 26)");
    myDescription.add(myAdvancedProfilingLabel, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
    myDescription.add(panel1, new GridConstraints(4, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, false));
    myCpuRecordingRadio = new JBRadioButton();
    myCpuRecordingRadio.setEnabled(false);
    myCpuRecordingRadio.setText("CPU activity (Requires API level >= 26)");
    panel1.add(myCpuRecordingRadio, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
    myMemoryRecordingRadio = new JBRadioButton();
    myMemoryRecordingRadio.setEnabled(false);
    myMemoryRecordingRadio.setMargin(new Insets(0, 0, 0, 0));
    myMemoryRecordingRadio.setText("Native memory activity (Requires API level >= 29)");
    panel1.add(myMemoryRecordingRadio, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           null, null, null, 0, false));
    myStartupCpuConfigsComboBox = new ComboBox();
    myStartupCpuConfigsComboBox.setEnabled(false);
    panel1.add(myStartupCpuConfigsComboBox, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                null, new Dimension(350, -1), null, 0, false));
    myNativeMemoryProfilerSampleRate = new LabeledComponent();
    try {
      myNativeMemoryProfilerSampleRate.setComponentClass("com.intellij.ui.components.JBTextField");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    myNativeMemoryProfilerSampleRate.setLabelInsets(new Insets(0, 6, 0, 0));
    myNativeMemoryProfilerSampleRate.setLabelLocation("West");
    myNativeMemoryProfilerSampleRate.setText("Native memory sampling interval (bytes)");
    myDescription.add(myNativeMemoryProfilerSampleRate,
                      new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                          false));
    myStartupCpuProfilerDescription = new JTextPane();
    myStartupCpuProfilerDescription.setAutoscrolls(false);
    myStartupCpuProfilerDescription.setDoubleBuffered(false);
    myStartupCpuProfilerDescription.setEditable(false);
    myStartupCpuProfilerDescription.setEnabled(true);
    Font myStartupCpuProfilerDescriptionFont = getFont(null, -1, -1, myStartupCpuProfilerDescription.getFont());
    if (myStartupCpuProfilerDescriptionFont != null) myStartupCpuProfilerDescription.setFont(myStartupCpuProfilerDescriptionFont);
    myStartupCpuProfilerDescription.setMargin(new Insets(0, 3, 0, 3));
    myStartupCpuProfilerDescription.setText("You must select Run > Profile from the main menu and deploy your app to a device");
    myDescription.add(myStartupCpuProfilerDescription,
                      new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, 1, 1, null, null, null, 0,
                                          false));
  }

  private Font getFont(String fontName, int style, int size, Font currentFont) {
    if (currentFont == null) return null;
    String resultName;
    if (fontName == null) {
      resultName = currentFont.getName();
    }
    else {
      Font testFont = new Font(fontName, Font.PLAIN, 10);
      if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
        resultName = fontName;
      }
      else {
        resultName = currentFont.getName();
      }
    }
    Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
    Font fontWithFallback = isMac
                            ? new Font(font.getFamily(), font.getStyle(), font.getSize())
                            : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
    return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
  }
}
