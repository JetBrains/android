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

import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.idea.fd.InstantRunGradleUtils;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunStatsService;
import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.tasks.LaunchTasksProviderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLaunchTasksProviderFactory implements LaunchTasksProviderFactory {
  private final AndroidRunConfigurationBase myRunConfig;
  private final ExecutionEnvironment myEnv;
  private final AndroidFacet myFacet;
  private final ApkProvider myApkProvider;
  private final LaunchOptions myLaunchOptions;
  private final ProcessHandler myPreviousSessionProcessHandler;

  public AndroidLaunchTasksProviderFactory(@NotNull AndroidRunConfigurationBase runConfig,
                                           @NotNull ExecutionEnvironment env,
                                           @NotNull AndroidFacet facet,
                                           @NotNull ApkProvider apkProvider,
                                           @NotNull LaunchOptions launchOptions,
                                           @Nullable ProcessHandler processHandler) {
    myRunConfig = runConfig;
    myEnv = env;
    myFacet = facet;
    myApkProvider = apkProvider;
    myLaunchOptions = launchOptions;
    myPreviousSessionProcessHandler = processHandler;
  }

  @NotNull
  @Override
  public LaunchTasksProvider get() {
    InstantRunStatsService.get(myEnv.getProject()).notifyDeployStarted();

    if (InstantRunUtils.isInstantRunEnabled(myEnv)) {
      InstantRunBuildInfo buildInfo = getInstantRunBuildInfo();
      if (buildInfo != null) {
        InstantRunManager.LOG.info("Build timestamp: " +
                                   buildInfo.getTimeStamp() +
                                   ", verifier status: " +
                                   StringUtil.notNullize(buildInfo.getVerifierStatus(), "empty"));

        // If there is nothing new to deploy, and there is an existing run session connected to the process,
        // then we should not use AndroidLaunchTasksProvider since it assumes that there will be a new process created,
        // and unnecessarily terminates and restarts the existing session. The termination is troublesome because restarting
        // a process without proper cold swap patches could result in loss of updates from all previous hotswaps.
        if (myPreviousSessionProcessHandler != null &&
            !myPreviousSessionProcessHandler.isProcessTerminated() &&
            buildInfo.hasNoChanges()) {
          return new NoChangesTasksProvider(myFacet);
        }
        else if (canHotSwap(buildInfo)) {
          return new HotSwapTasksProvider(myRunConfig, myEnv, myFacet, myApkProvider, myLaunchOptions);
        }
      }
      else {
        InstantRunManager.LOG.info("No build-info.xml file");
      }
    }

    return new AndroidLaunchTasksProvider(myRunConfig, myEnv, myFacet, myApkProvider, myLaunchOptions);
  }

  /** Returns whether the build results indicate that we can perform a hot swap */
  private boolean canHotSwap(@NotNull InstantRunBuildInfo info) {
    if (myPreviousSessionProcessHandler == null) {
      // if there is no existing session, then even though the build ids might match, we can't use hotswap (possibly use dexswap)
      InstantRunManager.LOG.info("Cannot hot swap since there is no active launch session for this config.");
      return false;
    }

    if (InstantRunUtils.needsFullBuild(myEnv) || !InstantRunUtils.isAppRunning(myEnv)) {
      // We knew before the build that we couldn't hot swap..
      return false;
    }

    boolean canHotswap = info.canHotswap();
    if (!canHotswap) {
      InstantRunManager.LOG.info("Cannot hot swap according to build-info.xml");
    }

    return canHotswap;
  }

  @Nullable
  private InstantRunBuildInfo getInstantRunBuildInfo() {
    AndroidGradleModel model = AndroidGradleModel.get(myFacet);
    return model == null ? null : InstantRunGradleUtils.getBuildInfo(model);
  }
}
