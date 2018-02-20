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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.fd.DeployType;
import com.android.tools.idea.fd.InstantRunContext;
import com.android.tools.idea.fd.InstantRunStatsService;
import com.android.tools.ir.client.InstantRunArtifactType;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@see SplitApkDeployTaskContext} when deploying an application
 * with Instant-Run split-apks support.
 */
public class SplitApkDeployTaskInstantRunContext implements SplitApkDeployTaskContext {
  @NotNull
  private final InstantRunContext myContext;

  @NotNull
  private NotNullLazyValue<InstantRunBuildInfo> myBuildInfo;

  public SplitApkDeployTaskInstantRunContext(@NotNull InstantRunContext context) {
    myContext = context;
    myBuildInfo = new NotNullLazyValue<InstantRunBuildInfo>() {
      @NotNull
      @Override
      protected InstantRunBuildInfo compute() {
        InstantRunBuildInfo result = myContext.getInstantRunBuildInfo();
        if (result == null) {
          // We would not get to this point if InstantRun build info was not present.
          getLogger().error("Unable to retrieve InstantRun build info");
          throw new IllegalStateException();
        }
        return result;
      }
    };
  }

  @NotNull
  @Override
  public String getApplicationId() {
    return myContext.getApplicationId();
  }

  @Override
  public boolean isPatchBuild() {
    return getBuildInfo().isPatchBuild();
  }

  @NotNull
  @Override
  public List<File> getArtifacts() {
    return getBuildInfo().getArtifacts().stream()
      .filter(artifact -> artifact.type == InstantRunArtifactType.SPLIT_MAIN || artifact.type == InstantRunArtifactType.SPLIT)
      .map(artifact -> artifact.file)
      .collect(Collectors.toList());
  }

  @Override
  public void notifyInstall(@NotNull Project project, @NotNull IDevice device, boolean status) {
    assert myContext.getBuildSelection() != null;
    InstantRunStatsService.get(project).notifyDeployType(DeployType.SPLITAPK, myContext, device);
  }

  @NotNull
  private InstantRunBuildInfo getBuildInfo() {
    return myBuildInfo.getValue();
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(SplitApkDeployTaskInstantRunContext.class);
  }
}
