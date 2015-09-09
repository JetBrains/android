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
package org.jetbrains.android.run;

import com.android.tools.idea.run.CloudConfigurationProvider;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A run configuration execution state that just launches a cloud device.
 * TODO: This should probably not be a run configuration execution, since it does not actually execute any project code.
 * Rather, the device should be launched before the run configuration execution where it is actually used.
 */
public class CloudDeviceLaunchRunningState implements RunProfileState {

  @NotNull private final AndroidFacet myFacet;
  private final int myCloudConfigurationId;
  @NotNull private final String myCloudProjectId;

  public CloudDeviceLaunchRunningState(@NotNull AndroidFacet facet, int cloudConfigurationId, @NotNull String cloudProjectId) {
    myFacet = facet;
    myCloudConfigurationId = cloudConfigurationId;
    myCloudProjectId = cloudProjectId;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    final CloudConfigurationProvider provider = CloudConfigurationProvider.getCloudConfigurationProvider();
    assert provider != null;
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        provider.launchCloudDevice(myCloudConfigurationId, myCloudProjectId, myFacet);
      }
    });
    // This duplicates what previously happened in AndroidRunningState, but may not be necessary.
    ProcessHandler processHandler = new DefaultDebugProcessHandler();
    AndroidProcessText.attach(processHandler);
    return new DefaultExecutionResult(null /* console */, processHandler);
  }
}
