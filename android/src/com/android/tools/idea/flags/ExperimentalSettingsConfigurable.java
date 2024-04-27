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
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.android.util.AndroidBundle;
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
      return AndroidBundle.message("configurable.ExperimentalSettingsConfigurable.display.name");
    }
    else {
      return AndroidBundle.message("configurable.ExperimentalSettingsConfigurable.idea.display.name");
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
}
