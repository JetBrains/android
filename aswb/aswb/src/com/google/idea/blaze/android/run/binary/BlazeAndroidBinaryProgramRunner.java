/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.binary;

import static com.intellij.openapi.application.ModalityState.NON_MODAL;

import com.android.tools.idea.execution.common.AndroidConfigurationExecutor;
import com.android.tools.idea.profilers.ProfileRunExecutor;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.AsyncProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ActionsKt;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

/** Program runner for configurations from {@link BlazeAndroidBinaryRunConfigurationHandler}. */
public class BlazeAndroidBinaryProgramRunner extends AsyncProgramRunner<RunnerSettings> {
  @Override
  public boolean canRun(String executorId, RunProfile profile) {
    BlazeAndroidRunConfigurationHandler handler =
        BlazeAndroidRunConfigurationHandler.getHandlerFrom(profile);
    if (!(handler instanceof BlazeAndroidBinaryRunConfigurationHandler)) {
      return false;
    }
    return (DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)
        || DefaultRunExecutor.EXECUTOR_ID.equals(executorId)
        || ProfileRunExecutor.EXECUTOR_ID.equals(executorId));
  }

  @Override
  protected Promise<RunContentDescriptor> execute(
      ExecutionEnvironment environment, RunProfileState state) {
    FileDocumentManager.getInstance().saveAllDocuments();

    AsyncPromise<RunContentDescriptor> promise = new AsyncPromise<>();

    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(environment.getProject(), "Launching ${runProfile.name}") {
              @Override
              public void run(ProgressIndicator indicator) {
                try {
                  RunContentDescriptor descriptor;
                  if (state instanceof AndroidConfigurationExecutor) {
                    AndroidConfigurationExecutor configurationExecutor =
                        (AndroidConfigurationExecutor) state;
                    Executor executor = environment.getExecutor();
                    if (executor.getId().equals(DefaultDebugExecutor.EXECUTOR_ID)) {
                      descriptor = configurationExecutor.debug(indicator);
                    } else if (executor.getId().equals(DefaultRunExecutor.EXECUTOR_ID)
                        || executor.getId().equals(ProfileRunExecutor.EXECUTOR_ID)) {
                      descriptor = configurationExecutor.run(indicator);
                    } else {
                      throw new ExecutionException("Unsupported executor");
                    }
                  } else {
                    descriptor = doExecute(state, environment);
                  }
                  promise.setResult(descriptor);
                } catch (ExecutionException e) {
                  boolean unused = promise.setError(e);
                }
              }

              @Override
              public void onCancel() {
                super.onCancel();
                promise.setResult(null);
              }
            });

    return promise;
  }

  private RunContentDescriptor doExecute(
      final RunProfileState state, final ExecutionEnvironment env) throws ExecutionException {
    ExecutionResult result = state.execute(env.getExecutor(), this);
    return ActionsKt.invokeAndWaitIfNeeded(
        NON_MODAL,
        () -> new RunContentBuilder(result, env).showRunContent(env.getContentToReuse()));
  }

  @Override
  public String getRunnerId() {
    return "AndroidBinaryProgramRunner";
  }
}
