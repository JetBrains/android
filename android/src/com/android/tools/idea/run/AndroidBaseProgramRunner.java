/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.DefaultProgramRunnerKt;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AndroidBaseProgramRunner extends GenericProgramRunner {
  @Override
  @Nullable
  protected RunContentDescriptor doExecute(@NotNull final RunProfileState state, @NotNull final ExecutionEnvironment env)
    throws ExecutionException {
    boolean showRunContent = env.getRunProfile() instanceof AndroidTestRunConfiguration;
    RunnerAndConfigurationSettings runnerAndConfigurationSettings = env.getRunnerAndConfigurationSettings();
    if (runnerAndConfigurationSettings != null) {
      runnerAndConfigurationSettings.setActivateToolWindowBeforeRun(showRunContent);
    }

    FileDocumentManager.getInstance().saveAllDocuments();
    ExecutionResult result = state.execute(env.getExecutor(), this);
    RunContentDescriptor descriptor = DefaultProgramRunnerKt.showRunContent(result, env);
    if (descriptor != null) {
      ProcessHandler processHandler = descriptor.getProcessHandler();
      assert processHandler != null;

      RunProfile runProfile = env.getRunProfile();
      int uniqueId = runProfile instanceof RunConfigurationBase ? ((RunConfigurationBase)runProfile).getUniqueID() : -1;
      AndroidSessionInfo sessionInfo = new AndroidSessionInfo(processHandler, descriptor, uniqueId, env.getExecutor().getId(),
                                                              env.getExecutor().getActionName(), InstantRunUtils.isInstantRunEnabled(env));
      processHandler.putUserData(AndroidSessionInfo.KEY, sessionInfo);
    }

    return descriptor;
  }
}
