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
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.android.tools.idea.fd.*;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.tasks.LaunchTasksProviderFactory;
import com.android.tools.idea.run.tasks.UpdateSessionTasksProvider;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AndroidLaunchTasksProviderFactory implements LaunchTasksProviderFactory {
  private final AndroidRunConfigurationBase myRunConfig;
  private final ExecutionEnvironment myEnv;
  private final AndroidFacet myFacet;
  private final ApplicationIdProvider myApplicationIdProvider;
  private final ApkProvider myApkProvider;
  private final DeviceFutures myDeviceFutures;
  private final LaunchOptions myLaunchOptions;
  private final ProcessHandler myPreviousSessionProcessHandler;
  private final InstantRunContext myInstantRunContext;

  public AndroidLaunchTasksProviderFactory(@NotNull AndroidRunConfigurationBase runConfig,
                                           @NotNull ExecutionEnvironment env,
                                           @NotNull AndroidFacet facet,
                                           @NotNull ApplicationIdProvider applicationIdProvider,
                                           @NotNull ApkProvider apkProvider,
                                           @NotNull DeviceFutures deviceFutures,
                                           @NotNull LaunchOptions launchOptions,
                                           @Nullable ProcessHandler processHandler,
                                           @Nullable InstantRunContext instantRunContext) {
    myRunConfig = runConfig;
    myEnv = env;
    myFacet = facet;
    myApplicationIdProvider = applicationIdProvider;
    myApkProvider = apkProvider;
    myDeviceFutures = deviceFutures;
    myLaunchOptions = launchOptions;
    myPreviousSessionProcessHandler = processHandler;
    myInstantRunContext = instantRunContext;
  }

  @NotNull
  @Override
  public LaunchTasksProvider get() {
    Project project = myEnv.getProject();
    InstantRunStatsService.get(project).notifyDeployStarted();

    InstantRunBuildAnalyzer analyzer = null;
    InstantRunBuildInfo instantRunBuildInfo = myInstantRunContext != null ? myInstantRunContext.getInstantRunBuildInfo() : null;
    if (instantRunBuildInfo != null) {
      analyzer = new InstantRunBuildAnalyzer(project, myInstantRunContext, myPreviousSessionProcessHandler, getApks(),
                                             InstantRunSettings.isRestartActivity());

      if (InstantRunSettings.isRecorderEnabled()) {
        if (!myDeviceFutures.getDevices().isEmpty()) { // Instant Run is guaranteed to be for exactly 1 device
          FlightRecorder.get(project).setLaunchTarget(myDeviceFutures.getDevices().get(0));
        }
        FlightRecorder.get(project).saveBuildInfo(instantRunBuildInfo);
      }
    }

    if (analyzer != null && analyzer.canReuseProcessHandler()) {
      return new UpdateSessionTasksProvider(analyzer, myLaunchOptions);
    }

    return new AndroidLaunchTasksProvider(myRunConfig, myEnv, myFacet, analyzer, myApplicationIdProvider, myApkProvider, myLaunchOptions);
  }

  @NotNull
  private Collection<ApkInfo> getApks() {
    try {
      List<IDevice> devices = myDeviceFutures.getIfReady();
      if (devices != null && !devices.isEmpty()) {
        return myApkProvider.getApks(devices.get(0));
      }
    }
    catch (ApkProvisionException e) {
      InstantRunManager.LOG.warn("Unable to get APKs from APK Provider: ", e);
    }
    return Collections.EMPTY_LIST;
  }
}
