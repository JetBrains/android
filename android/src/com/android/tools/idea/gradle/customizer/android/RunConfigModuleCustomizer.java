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
package com.android.tools.idea.gradle.customizer.android;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.run.TargetSelectionMode;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.util.Facets.findFacet;
import static org.jetbrains.android.util.AndroidUtils.addRunConfiguration;

/**
 * Creates run configurations for modules imported from {@link com.android.builder.model.AndroidProject}s.
 */
public class RunConfigModuleCustomizer implements ModuleCustomizer<AndroidGradleModel> {
  @Override
  public void customizeModule(@NotNull Project project,
                              @NotNull Module module,
                              @NotNull IdeModifiableModelsProvider modelsProvider,
                              @Nullable AndroidGradleModel androidModel) {
    if (androidModel != null) {
      AndroidFacet facet = findFacet(module, modelsProvider, AndroidFacet.ID);

      if (facet != null && !facet.isLibraryProject()) {
        RunManager runManager = RunManager.getInstance(project);
        ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
        List<RunConfiguration> configs = runManager.getConfigurationsList(configurationFactory.getType());
        for (RunConfiguration config : configs) {
          if (config instanceof AndroidRunConfiguration) {
            AndroidRunConfiguration androidRunConfig = (AndroidRunConfiguration)config;
            if (androidRunConfig.getConfigurationModule().getModule() == module) {
              // There is already a run configuration for this module.
              return;
            }
          }
        }
        addRunConfiguration(facet, null, false, TargetSelectionMode.SHOW_DIALOG, null);
      }
    }
  }
}
