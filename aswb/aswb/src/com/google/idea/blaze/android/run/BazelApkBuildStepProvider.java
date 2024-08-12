/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.idea.blaze.android.run;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.run.binary.mobileinstall.MobileInstallBuildStep;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeInstrumentationTestApkBuildStep;
import com.google.idea.blaze.android.run.runner.FullApkBuildStep;
import com.google.idea.blaze.android.run.runner.InstrumentationInfo;
import com.google.idea.blaze.android.run.runner.InstrumentationInfo.InstrumentationParserException;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

/** Provides APK build step for Bazel projects. */
public class BazelApkBuildStepProvider implements ApkBuildStepProvider {
  private static final Logger logger = Logger.getInstance(BazelApkBuildStepProvider.class);

  @Override
  public ApkBuildStep getBinaryBuildStep(
      Project project,
      boolean useMobileInstall,
      boolean nativeDebuggingEnabled,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      String launchId) {
    if (useMobileInstall) {
      return new MobileInstallBuildStep(project, label, blazeFlags, exeFlags, launchId);
    } else {
      return new FullApkBuildStep(project, label, blazeFlags, nativeDebuggingEnabled);
    }
  }

  @Override
  public ApkBuildStep getAitBuildStep(
      Project project,
      boolean useMobileInstall,
      boolean nativeDebuggingEnabled,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      String launchId)
      throws ExecutionException {
    if (useMobileInstall) {
      return new MobileInstallBuildStep(project, label, blazeFlags, exeFlags, launchId);
    }

    BlazeProjectData data =
        Preconditions.checkNotNull(
            BlazeProjectDataManager.getInstance(project).getBlazeProjectData());
    InstrumentationInfo info;
    try {
      info = InstrumentationInfo.getInstrumentationInfo(label, data);
    } catch (InstrumentationParserException e) {
      logger.warn("Could not get instrumentation info: " + e.getMessage());
      throw new ExecutionException(e.getMessage(), e);
    }
    return new BlazeInstrumentationTestApkBuildStep(project, info, blazeFlags);
  }

  @Override
  public ImmutableSet<BuildSystemName> getSupportedBuildSystems() {
    return ImmutableSet.of(BuildSystemName.Bazel);
  }
}
