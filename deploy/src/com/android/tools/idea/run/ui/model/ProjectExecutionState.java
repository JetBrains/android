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
package com.android.tools.idea.run.ui.model;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class ProjectExecutionState implements Disposable {
  private final Set<ExecutionInstance> myRunningExecutors = new HashSet<>();

  @NotNull
  public static ProjectExecutionState getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectExecutionState.class);
  }

  public boolean isAnyExecutorInScheduledState() {
    return !myRunningExecutors.isEmpty();
  }

  @Override
  public void dispose() {
    myRunningExecutors.clear();
  }

  private ProjectExecutionState() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStartScheduled(@NotNull String executorId, @NotNull ExecutionEnvironment environment) {
        myRunningExecutors.add(new ExecutionInstance(executorId, environment.getRunner().getRunnerId()));
      }

      @Override
      public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment environment) {
        myRunningExecutors.remove(new ExecutionInstance(executorId, environment.getRunner().getRunnerId()));
      }

      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment environment, @NotNull ProcessHandler handler) {
        myRunningExecutors.remove(new ExecutionInstance(executorId, environment.getRunner().getRunnerId()));
      }
    });
  }

  private static class ExecutionInstance {
    public final String myExecutorId;
    public final String myRunnerId;

    private ExecutionInstance(String executorId, String runnerId) {
      myExecutorId = executorId;
      myRunnerId = runnerId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(myExecutorId, myRunnerId);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExecutionInstance &&
             myExecutorId.equals(((ExecutionInstance)obj).myExecutorId) &&
             myRunnerId.equals(((ExecutionInstance)obj).myRunnerId);
    }
  }
}
