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

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.adtui.ui.ClickableLabel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.upgrade.AndroidPluginVersionUpdater;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/**
 * The configuration panel for the Android profiler settings.
 */
public class AndroidProfilersPanel implements HyperlinkListener {

  private static final String MINIMUM_GRADLE_PLUGIN_VERSION_STRING = "2.4.0";
  private static final GradleVersion MINIMUM_GRADLE_PLUGIN_VERSION = GradleVersion.parse(MINIMUM_GRADLE_PLUGIN_VERSION_STRING);

  private final Project myProject;
  private JPanel myDescription;
  private JTextPane myNativeMemoryRateProfilerDescription;
  // TODO(b/112536124): vertical gap between checkbox and text doesn't toggle the checkbox
  private JCheckBox myAdvancedProfilingCheckBox;
  private ClickableLabel myAdvancedProfilingLabel;
  private HyperlinkLabel myHyperlinkLabel;
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
    updateHyperlink("");
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

    if (!StudioFlags.PROFILER_ENABLE_NATIVE_SAMPLE.get()) {
      myMemoryRecordingRadio.setVisible(false);
      myNativeMemoryProfilerSampleRate.setVisible(false);
    }
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

    myStartupProfileCheckBox.setSelected(state.STARTUP_PROFILING_ENABLED);
    myCpuRecordingRadio.setSelected(state.STARTUP_CPU_PROFILING_ENABLED);
    myMemoryRecordingRadio.setSelected(state.STARTUP_NATIVE_MEMORY_PROFILING_ENABLED);
    myStartupCpuProfilerDescription.setBackground(myDescription.getBackground());
    myStartupCpuProfilerDescription.setForeground(UIUtil.getContextHelpForeground());

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

    state.STARTUP_CPU_PROFILING_ENABLED = StudioFlags.PROFILER_STARTUP_CPU_PROFILING.get() && myCpuRecordingRadio.isSelected();
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
    // TODO: Hyperlink label has a fixed 2 pixel offset at the beginning and cannot be indented. Change for a good component later.
    myHyperlinkLabel = new HyperlinkLabel();
    myHyperlinkLabel.addHyperlinkListener(this);

    myAdvancedProfilingDescription =
      SwingHelper.createHtmlViewer(true, null, UIUtil.getPanelBackground(), UIUtil.getContextHelpForeground());
    myAdvancedProfilingDescription.setText(
      "<html>Adds support for network payloads, the event timeline, allocated object count and garbage collection events on devices" +
      " running API level < 26. May slightly increase build time due to compile-time instrumentation. Has no effect on devices running" +
      " API level >= 26. <a href=\"https://developer.android.com/r/studio-ui/profiler/support-for-older-devices\">Learn more</a></html>");
    myAdvancedProfilingDescription.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
  }

  private void updateHyperlink(String message) {
    boolean supported = true;
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      AndroidModuleModel model = AndroidModuleModel.get(module);
      if (model != null) {
        GradleVersion modelVersion = model.getModelVersion();
        supported = modelVersion != null && modelVersion.compareIgnoringQualifiers(MINIMUM_GRADLE_PLUGIN_VERSION) >= 0;
        if (!supported) {
          break;
        }
      }
    }
    myHyperlinkLabel
      .setHyperlinkText("This feature can only be enabled with a gradle plugin version of 2.4 or greater. ", "Update project", "");
    myHyperlinkLabel.setVisible(!supported);
    myHyperlinkLabel.setVisible(!supported);
    myDescription.setEnabled(supported);
    myAdvancedProfilingCheckBox.setEnabled(supported);
    myAdvancedProfilingLabel.setEnabled(supported);
  }

  @Override
  public void hyperlinkUpdate(HyperlinkEvent e) {
    GradleVersion gradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION);
    GradleVersion pluginVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get());

    // Don't bother sync'ing if latest is less than our minimum requirement.
    if (MINIMUM_GRADLE_PLUGIN_VERSION.compareIgnoringQualifiers(pluginVersion) > 0) {
      updateHyperlink("(No matching gradle version found)");
      return;
    }

    // Update plugin version
    AndroidPluginVersionUpdater updater = AndroidPluginVersionUpdater.getInstance(myProject);
    AndroidPluginVersionUpdater.UpdateResult result = updater.updatePluginVersion(pluginVersion, gradleVersion, null);
    if (result.isPluginVersionUpdated() && result.versionUpdateSuccess()) {
      requestSync();
    }
    else {
      updateHyperlink("(Update failed)");
    }
  }

  private void requestSync() {
    SettableFuture<ProjectSystemSyncManager.SyncResult> syncResult = SettableFuture.create();

    ApplicationManager.getApplication().invokeLater(() -> {
      updateHyperlink("(Syncing...)");

      // TODO change trigger to plugin upgrade trigger if it is created
      syncResult.setFuture(ProjectSystemUtil.getProjectSystem(myProject)
                             .getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED));
    });

    // Block until sync finishes
    if (Futures.getUnchecked(syncResult).isSuccessful()) {
      updateHyperlink("");
    }
    else {
      updateHyperlink("(Sync failed)");
    }
  }
}
