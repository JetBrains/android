/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.flags;

import com.android.tools.idea.flags.ExperimentalConfigurable.ApplyState;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExperimentalSettingsConfigurable extends CompositeConfigurable<ExperimentalConfigurable> implements SearchableConfigurable {
  private JPanel myPanel;
  private JPanel myExtensionPanel;

  private final Project myProject;
  private final SortedMap<String, ExperimentalConfigurable> myConfigurableMap;

  private final boolean forAndroidStudio;

  ExperimentalSettingsConfigurable(@NotNull Project project, boolean studio) {
    myConfigurableMap = new TreeMap<>();
    myProject = project;
    forAndroidStudio = studio;
    setupUI();
    reset();
  }

  @Override
  @NotNull
  public String getId() {
    if (forAndroidStudio) {
      return "experimental";
    }
    else {
      return "experimentalPlugin";
    }
  }

  @Override
  @Nls
  public String getDisplayName() {
    if (forAndroidStudio) {
      return "Experimental";
    }
    else {
      return "Android (Experimental)";
    }
  }

  @Override
  @NotNull
  public List<ExperimentalConfigurable> createConfigurables() {
    for (ExperimentalSettingsContributor contributor : ExperimentalSettingsContributor.EP_NAME.getExtensions()) {
      if (contributor.shouldCreateConfigurable(myProject)) {
        ExperimentalConfigurable configurable = contributor.createConfigurable(myProject);
        String name = contributor.getName();
        myConfigurableMap.put(name, configurable);
      }
    }
    return myConfigurableMap.values().stream().toList();
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    myExtensionPanel.setLayout(new BoxLayout(myExtensionPanel, BoxLayout.PAGE_AXIS));
    myConfigurableMap.forEach((name, configurable) -> {
      myExtensionPanel.add(new JLabel(" "));
      myExtensionPanel.add(new TitledSeparator(name));
      myExtensionPanel.add(configurable.createComponent());
    });
    return myPanel;
  }

  @Override
  public void apply() throws ConfigurationException {
    boolean shouldApply = true;
    boolean shouldAskForRestart = false;
    boolean shouldRestart = false;
    Application application = ApplicationManager.getApplication();
    for (ExperimentalConfigurable configurable : getConfigurables()) {
      ApplyState state = configurable.preApplyCallback();
      shouldApply &= (state != ApplyState.BLOCK);
      shouldAskForRestart |= (state == ApplyState.RESTART);
    }
    // Don't ask for restart in unit test mode
    shouldAskForRestart &= !application.isUnitTestMode();
    if (!shouldApply) return;
    if (shouldAskForRestart) {
      String action = application.isRestartCapable() ? "Restart" : "Shutdown";
      int result =
        Messages.showYesNoDialog("Android Studio must be restarted for changes to take effect.", "Restart Required", action, "Cancel",
                                 Messages.getQuestionIcon());
      if (result == Messages.YES) {
        shouldRestart = true;
      }
      else {
        return;
      }
    }
    super.apply();
    for (ExperimentalConfigurable configurable : getConfigurables()) {
      configurable.postApplyCallback();
    }
    if (shouldRestart) {
      // Suppress "Are you sure you want to exit Android Studio" dialog, and restart if possible.
      ApplicationManager.getApplication().exit(false, true, true);
    }
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("<html><b>Note:</b> These settings are for features that are considered <b>experimental</b>.</html>");
    myPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                              false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null,
                                             0, false));
    myExtensionPanel = new JPanel();
    myExtensionPanel.setLayout(new BorderLayout(0, 0));
    myPanel.add(myExtensionPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                      null, null, 0, false));
  }
}
