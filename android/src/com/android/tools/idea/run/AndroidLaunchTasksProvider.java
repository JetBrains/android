/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.deploy.DeploymentConfiguration;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.run.tasks.AbstractDeployTask;
import com.android.tools.idea.run.tasks.ApplyChangesTask;
import com.android.tools.idea.run.tasks.ApplyCodeChangesTask;
import com.android.tools.idea.run.tasks.DeployTask;
import com.android.tools.idea.run.util.SwapInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class AndroidLaunchTasksProvider {
  private final Logger myLogger = Logger.getInstance(AndroidLaunchTasksProvider.class);
  private final ExecutionEnvironment myEnv;
  private final ApkProvider myApkProvider;
  private final Project myProject;

  public AndroidLaunchTasksProvider(@NotNull ExecutionEnvironment env,
                                    @NotNull AndroidFacet facet,
                                    @NotNull ApkProvider apkProvider
  ) {
    myEnv = env;
    myProject = facet.getModule().getProject();
    myApkProvider = apkProvider;
  }

  @NotNull
  public AbstractDeployTask getDeployTask(@NotNull final IDevice device) throws ExecutionException {
    DeployType deployType = getDeployType();
    AndroidRunConfiguration config = (AndroidRunConfiguration)myEnv.getRunProfile();
    List<String> disabledFeatures = config.getDisabledDynamicFeatures();
    // Add packages to the deployment, filtering out any dynamic features that are disabled.
    Collection<ApkInfo> apks = null;
    try {
      apks = myApkProvider.getApks(device);
    }
    catch (ApkProvisionException e) {
      throw new ExecutionException(e);
    }
    List<ApkInfo> packages = apks.stream()
      .map(apkInfo -> filterDisabledFeatures(apkInfo, disabledFeatures))
      .collect(Collectors.toList());
    switch (deployType) {
      case APPLY_CHANGES:
        return new ApplyChangesTask(
          myProject,
          packages,
          isApplyChangesFallbackToRun(),
          config.ALWAYS_INSTALL_WITH_PM);
      case APPLY_CODE_CHANGES:
        return new ApplyCodeChangesTask(
          myProject,
          packages,
          isApplyCodeChangesFallbackToRun(),
          config.ALWAYS_INSTALL_WITH_PM);
      case DEPLOY:
        return new DeployTask(
          myProject,
          packages,
          config.PM_INSTALL_OPTIONS,
          config.ALL_USERS,
          config.ALWAYS_INSTALL_WITH_PM);
      default:
        throw new IllegalStateException("Unhandled Deploy Type");
    }
  }

  private boolean isApplyCodeChangesFallbackToRun() {
    return DeploymentConfiguration.getInstance().APPLY_CODE_CHANGES_FALLBACK_TO_RUN;
  }

  private boolean isApplyChangesFallbackToRun() {
    return DeploymentConfiguration.getInstance().APPLY_CHANGES_FALLBACK_TO_RUN;
  }

  private boolean shouldApplyChanges() {
    SwapInfo swapInfo = myEnv.getUserData(SwapInfo.SWAP_INFO_KEY);
    return swapInfo != null && swapInfo.getType() == SwapInfo.SwapType.APPLY_CHANGES;
  }

  private boolean shouldApplyCodeChanges() {
    SwapInfo swapInfo = myEnv.getUserData(SwapInfo.SWAP_INFO_KEY);
    return swapInfo != null && swapInfo.getType() == SwapInfo.SwapType.APPLY_CODE_CHANGES;
  }

  private DeployType getDeployType() {
    if (shouldApplyChanges()) {
      return DeployType.APPLY_CHANGES;
    }
    else if (shouldApplyCodeChanges()) {
      return DeployType.APPLY_CODE_CHANGES;
    }
    else {
      return DeployType.DEPLOY;
    }
  }

  @NotNull
  private static ApkInfo filterDisabledFeatures(ApkInfo apkInfo, List<String> disabledFeatures) {
    if (apkInfo.getFiles().size() > 1) {
      List<ApkFileUnit> filtered = apkInfo.getFiles().stream()
        .filter(feature -> DynamicAppUtils.isFeatureEnabled(disabledFeatures, feature))
        .collect(Collectors.toList());
      return new ApkInfo(filtered, apkInfo.getApplicationId());
    }
    else {
      return apkInfo;
    }
  }

  private enum DeployType {
    APPLY_CHANGES {
      @Override
      public String asDisplayName() {
        return "Apply Changes";
      }
    },
    APPLY_CODE_CHANGES {
      @Override
      public String asDisplayName() {
        return "Apply Code Changes";
      }
    },
    DEPLOY {
      @Override
      public String asDisplayName() {
        return "Launch";
      }
    },
    ;

    public abstract String asDisplayName();
  }
}
