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
package com.android.tools.idea.project;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;
import static com.android.tools.idea.instantapp.InstantApps.getDefaultInstantAppUrl;
import static com.android.tools.idea.run.AndroidRunConfiguration.DO_NOTHING;
import static com.android.tools.idea.run.AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY;
import static com.android.tools.idea.run.util.LaunchUtils.isWatchFaceApp;

import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.run.TargetSelectionMode;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.util.PathUtil;
import java.util.List;
import kotlin.text.StringsKt;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidRunConfigurations {
  @NotNull
  public static AndroidRunConfigurations getInstance() {
    return ApplicationManager.getApplication().getService(AndroidRunConfigurations.class);
  }

  public void createRunConfiguration(@NotNull AndroidFacet facet) {
    Module module = facet.getModule();
    ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
    List<RunConfiguration> configurations =
      RunManager.getInstance(module.getProject()).getConfigurationsList(configurationFactory.getType());
    for (RunConfiguration configuration : configurations) {
      if (configuration instanceof AndroidRunConfiguration &&
          ((AndroidRunConfiguration)configuration).getConfigurationModule().getModule() == module) {
        // There is already a run configuration for this module.
        return;
      }
    }

    addRunConfiguration(facet, TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX);
  }

  public void addRunConfiguration(@NotNull AndroidFacet facet, @Nullable TargetSelectionMode targetSelectionMode) {
    Module module = facet.getModule();
    RunManager runManager = RunManager.getInstance(module.getProject());
    String projectNameInExternalSystemStyle = PathUtil.suggestFileName(module.getProject().getName(), true, false);
    String configurationName = StringsKt.removePrefix(module.getName(), projectNameInExternalSystemStyle + ".");
    RunnerAndConfigurationSettings settings = runManager.createConfiguration(configurationName, AndroidRunConfigurationType.class);
    AndroidRunConfiguration configuration = (AndroidRunConfiguration)settings.getConfiguration();
    configuration.setModule(module);

    if (facet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP) {
      configuration.setLaunchUrl(getDefaultInstantAppUrl(facet));
    }
    else if (isWatchFaceApp(facet)) {
      // In case of a watch face app, there is only a service and no default activity that can be launched
      // Eventually, we'd need to support launching a service, but currently you cannot launch a watch face service as well.
      // See https://code.google.com/p/android/issues/detail?id=151353
      configuration.MODE = DO_NOTHING;
    }
    else {
      configuration.MODE = LAUNCH_DEFAULT_ACTIVITY;
    }

    if (targetSelectionMode != null) {
      configuration.getDeployTargetContext().setTargetSelectionMode(targetSelectionMode);
    }
    if (!module.getProject().isDisposed()){
      runManager.addConfiguration(settings);
      runManager.setSelectedConfiguration(settings);
    }
  }
}
