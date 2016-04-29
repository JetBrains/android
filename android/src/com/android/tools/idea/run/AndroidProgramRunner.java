/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.run.testing.AndroidTestRunConfiguration;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public class AndroidProgramRunner extends DefaultProgramRunner {
  public static final String ANDROID_LOGCAT_CONTENT_ID = "Android Logcat";

  public static final Key<Client> ANDROID_DEBUG_CLIENT = new Key<Client>("ANDROID_DEBUG_CLIENT");
  public static final Key<AndroidVersion> ANDROID_DEVICE_API_LEVEL = new Key<AndroidVersion>("ANDROID_DEVICE_API_LEVEL");

  private RunContentDescriptor myDescriptor;

  @Override
  protected RunContentDescriptor doExecute(@NotNull final RunProfileState state, @NotNull final ExecutionEnvironment env)
    throws ExecutionException {
    boolean showRunContent = env.getRunProfile() instanceof AndroidTestRunConfiguration;
    RunnerAndConfigurationSettings runnerAndConfigurationSettings = env.getRunnerAndConfigurationSettings();
    if (runnerAndConfigurationSettings != null) {
      runnerAndConfigurationSettings.setActivateToolWindowBeforeRun(showRunContent);
    }

    myDescriptor = super.doExecute(state, env);
    if (myDescriptor != null) {
      ProcessHandler processHandler = myDescriptor.getProcessHandler();
      assert processHandler != null;

      RunProfile runProfile = env.getRunProfile();
      int uniqueId = runProfile instanceof AndroidRunConfigurationBase ? ((AndroidRunConfigurationBase)runProfile).getUniqueID() : -1;
      AndroidSessionInfo sessionInfo = new AndroidSessionInfo(processHandler, myDescriptor, uniqueId, env.getExecutor().getId(),
                                                              InstantRunUtils.isInstantRunEnabled(env));
      processHandler.putUserData(AndroidSessionInfo.KEY, sessionInfo);
    }

    return myDescriptor;
  }

  public RunContentDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Override
  @NotNull
  public String getRunnerId() {
    return "AndroidProgramRunner";
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    if (!DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && !DefaultRunExecutor.EXECUTOR_ID.equals(executorId)) {
      return false;
    }

    return profile instanceof AndroidRunConfigurationBase;
  }
}
