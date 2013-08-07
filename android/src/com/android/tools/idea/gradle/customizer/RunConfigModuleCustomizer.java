/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.util.Facets;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.run.AndroidRunConfiguration;
import org.jetbrains.android.run.AndroidRunConfigurationType;
import org.jetbrains.android.run.TargetSelectionMode;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates run configurations for modules imported from {@link com.android.builder.model.AndroidProject}s.
 */
public class RunConfigModuleCustomizer implements ModuleCustomizer {
  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject ideaAndroidProject) {
    if (ideaAndroidProject != null) {
      AndroidFacet facet = Facets.getFirstFacetOfType(module, AndroidFacet.ID);
      if (facet != null && !facet.isLibraryProject()) {
        RunManager runManager = RunManager.getInstance(project);
        ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
        RunConfiguration[] configs = runManager.getConfigurations(configurationFactory.getType());
        for (RunConfiguration config : configs) {
          if (config instanceof AndroidRunConfiguration) {
            AndroidRunConfiguration androidRunConfig = (AndroidRunConfiguration)config;
            if (androidRunConfig.getConfigurationModule().getModule() == module) {
              // There is already a run configuration for this module.
              return;
            }
          }
        }
        AndroidUtils.addRunConfiguration(facet, null, false, TargetSelectionMode.SHOW_DIALOG, null);
      }
    }
  }
}
