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
package com.android.tools.idea.deploy;

import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.DeploymentService;
import com.android.tools.idea.run.deployable.DeployableProvider;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxDeployableProvider;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeployActionsInitializer implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    updateDeployableProvider(project, RunManager.getInstance(project).getSelectedConfiguration());
  }

  @Contract("null -> null")
  @Nullable
  private static AndroidRunConfigurationBase getAndroidRunConfigurationbase(@Nullable RunnerAndConfigurationSettings configSettings) {
    if (configSettings == null) {
      return null; // No valid config settings available.
    }
    RunConfiguration config = configSettings.getConfiguration();
    if (!(config instanceof AndroidRunConfigurationBase)) {
      return null; // CodeSwap is enabled for non-Gradle-based Android, but isn't supported.
    }
    return (AndroidRunConfigurationBase)config;
  }

  @Contract("null -> null")
  @Nullable
  private static Module getModule(@Nullable AndroidRunConfigurationBase androidRunConfigurationBase) {
    return androidRunConfigurationBase == null ? null : androidRunConfigurationBase.getConfigurationModule().getModule();
  }

  @Contract("null -> null")
  @Nullable
  private static DeployableProvider getDeployTargetProvider(@Nullable RunnerAndConfigurationSettings configSettings) {
    AndroidRunConfigurationBase androidRunConfig = getAndroidRunConfigurationbase(configSettings);
    Module module = getModule(androidRunConfig);
    if (module == null) {
      return null; // We only support projects with Android facets, which needs to be in a module.
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null; // Only support projects with Android facets.
    }
    else if (facet.isDisposed()) {
      Logger.getInstance(DeployActionsInitializer.class).warn("Facet is disposed for the selected configuration.");
      return null;
    }

    if (androidRunConfig.getDeployTargetContext().getCurrentDeployTargetProvider() instanceof DeviceAndSnapshotComboBoxTargetProvider) {
      ApplicationIdProvider applicationIdProvider = androidRunConfig.getApplicationIdProvider(facet);
      return new DeviceAndSnapshotComboBoxDeployableProvider(module.getProject(), applicationIdProvider);
    }
    else {
      return null;
    }
  }

  private static boolean canOverrideDeployableProvider(@Nullable DeployableProvider provider) {
    return provider == null || provider instanceof DeviceAndSnapshotComboBoxDeployableProvider;
  }

  private static void updateDeployableProvider(@NotNull Project project, @Nullable RunnerAndConfigurationSettings configSettings) {
    DeploymentService deploymentService = DeploymentService.getInstance(project);
    if (canOverrideDeployableProvider(deploymentService.getDeployableProvider())) {
      deploymentService.setDeployableProvider(getDeployTargetProvider(configSettings));
    }
  }

  public static class MyRunManagerListener implements RunManagerListener {

    @NotNull private final Project myProject;

    public MyRunManagerListener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void runConfigurationSelected(@Nullable RunnerAndConfigurationSettings settings) {
      updateDeployableProvider(myProject, settings);
    }

    @Override
    public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings, @Nullable String existingId) {
      runConfigurationChanged(settings);
    }

    @Override
    public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
      if (settings.getConfiguration() == RunManager.getInstance(myProject).getSelectedConfiguration()) {
        updateDeployableProvider(myProject, settings);
      }
    }

    @Override
    public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
      if (settings.getConfiguration() == RunManager.getInstance(myProject).getSelectedConfiguration()) {
        updateDeployableProvider(myProject, null);
      }
    }
  }

  public static class MyFacetManagerAdapter extends FacetManagerAdapter {

    @NotNull private final Project myProject;

    public MyFacetManagerAdapter(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void beforeFacetRemoved(@NotNull Facet facet) {
      RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(myProject).getSelectedConfiguration();
      if (facet instanceof AndroidFacet && facet.getModule() == getModule(getAndroidRunConfigurationbase(selectedConfiguration))) {
        updateDeployableProvider(myProject, null);
      }
    }

    @Override
    public void facetAdded(@NotNull Facet facet) {
      RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(myProject).getSelectedConfiguration();
      if (facet instanceof AndroidFacet && facet.getModule() == getModule(getAndroidRunConfigurationbase(selectedConfiguration))) {
        updateDeployableProvider(myProject, selectedConfiguration);
      }
    }
  }
}
