/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.run;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AndroidSessionInfo {
  public static final Key<AndroidSessionInfo> KEY = new Key<AndroidSessionInfo>("KEY");
  public static final Key<Client> ANDROID_DEBUG_CLIENT = new Key<Client>("ANDROID_DEBUG_CLIENT");
  public static final Key<AndroidVersion> ANDROID_DEVICE_API_LEVEL = new Key<AndroidVersion>("ANDROID_DEVICE_API_LEVEL");

  @NotNull private final ProcessHandler myProcessHandler;
  private final RunContentDescriptor myDescriptor;
  @NotNull private final String myExecutorId;
  @NotNull private final String myExecutorActionName;
  private final int myRunConfigId;
  private final boolean myInstantRun;

  public AndroidSessionInfo(@NotNull ProcessHandler processHandler,
                            @NotNull RunContentDescriptor descriptor,
                            int runConfigId,
                            @NotNull String executorId,
                            @NotNull String executorActionName,
                            boolean instantRunEnabled) {
    myProcessHandler = processHandler;
    myDescriptor = descriptor;
    myRunConfigId = runConfigId;
    myExecutorId = executorId;
    myExecutorActionName = executorActionName;
    myInstantRun = instantRunEnabled;
  }

  @NotNull
  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  @NotNull
  public RunContentDescriptor getDescriptor() {
    return myDescriptor;
  }

  @NotNull
  public String getExecutorId() {
    return myExecutorId;
  }

  @NotNull
  public String getExecutorActionName() {
    return myExecutorActionName;
  }

  public boolean isInstantRun() {
    return myInstantRun;
  }

  @NotNull
  public List<IDevice> getDevices() {
    if (myProcessHandler instanceof AndroidProcessHandler) {
      return ((AndroidProcessHandler)myProcessHandler).getDevices();
    }
    else {
      Client client = myProcessHandler.getUserData(ANDROID_DEBUG_CLIENT);
      if (client != null) {
        return Collections.singletonList(client.getDevice());
      }
    }

    return Collections.emptyList();
  }

  public int getRunConfigurationId() {
    return myRunConfigId;
  }

  @Nullable
  public static AndroidSessionInfo findOldSession(@NotNull Project project, @Nullable Executor executor, int currentID) {
    // Note: There are 2 alternatives here:
    //    1. ExecutionManager.getInstance(project).getContentManager().getAllDescriptors()
    //    2. ExecutionManagerImpl.getInstance(project).getRunningDescriptors
    // The 2nd one doesn't work since its implementation relies on the same run descriptor to be alive as the one that is launched,
    // but that doesn't work for android debug sessions where we have 2 process handlers (one while installing and another while debugging)
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      if (handler.isProcessTerminated() || handler.isProcessTerminating()) {
        continue;
      }

      AndroidSessionInfo info = handler.getUserData(KEY);

      if (info != null &&
          currentID == info.getRunConfigurationId() &&
          (executor == null || executor.getId().equals(info.getExecutorId()))) {
        return info;
      }
    }
    return null;
  }
}
