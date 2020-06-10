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
import com.android.tools.idea.run.DeploymentService;
import com.android.tools.idea.run.deployable.DeployableProvider;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxDeployableProvider;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeployActionsInitializer implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    Disposable activityDisposable = ExtensionPointUtil.createExtensionDisposable(this, StartupActivity.POST_STARTUP_ACTIVITY);
    Disposer.register(project, activityDisposable);

    MessageBusConnection projectConnection = project.getMessageBus().connect(activityDisposable);
    projectConnection.subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      @Override
      public void runConfigurationSelected() {
        updateDeployableProvider(project, RunManager.getInstance(project).getSelectedConfiguration());
      }

      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings, @Nullable String existingId) {
        runConfigurationChanged(settings);
      }

      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        if (settings.getConfiguration() == RunManager.getInstance(project).getSelectedConfiguration()) {
          updateDeployableProvider(project, settings);
        }
      }

      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
        if (settings.getConfiguration() == RunManager.getInstance(project).getSelectedConfiguration()) {
          updateDeployableProvider(project, null);
        }
      }
    });

    projectConnection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
      @Override
      public void beforeFacetRemoved(@NotNull Facet facet) {
        RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(project).getSelectedConfiguration();
        if (facet instanceof AndroidFacet && facet.getModule() == getModule(getAndroidRunConfigurationbase(selectedConfiguration))) {
          updateDeployableProvider(project, null);
        }
      }

      @Override
      public void facetAdded(@NotNull Facet facet) {
        RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(project).getSelectedConfiguration();
        if (facet instanceof AndroidFacet && facet.getModule() == getModule(getAndroidRunConfigurationbase(selectedConfiguration))) {
          updateDeployableProvider(project, selectedConfiguration);
        }
      }
    });

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
      return new DeviceAndSnapshotComboBoxDeployableProvider(facet, androidRunConfig.getApplicationIdProvider(facet));
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
}
