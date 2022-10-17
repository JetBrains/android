// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.android.tools.idea.run;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.deployable.SwappableProcessHandler;
import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidSessionInfo {
  public static final Key<AndroidSessionInfo> KEY = new Key<>("KEY");
  public static final Key<AndroidVersion> ANDROID_DEVICE_API_LEVEL = new Key<>("ANDROID_DEVICE_API_LEVEL");

  @NotNull private final ProcessHandler myProcessHandler;
  @NotNull private final String myExecutorId;
  @Nullable private final RunConfiguration myRunConfiguration;
  @NotNull private final ExecutionTarget myExecutionTarget;

  @NotNull
  public static AndroidSessionInfo create(@NotNull ProcessHandler processHandler,
                                          @Nullable RunConfiguration runConfiguration,
                                          @NotNull String executorId,
                                          @NotNull ExecutionTarget executionTarget) {
    AndroidSessionInfo result = new AndroidSessionInfo(processHandler, runConfiguration, executorId, executionTarget);
    processHandler.putUserData(KEY, result);
    return result;
  }

  private AndroidSessionInfo(@NotNull ProcessHandler processHandler,
                             @Nullable RunConfiguration runConfiguration,
                             @NotNull String executorId,
                             @NotNull ExecutionTarget executionTarget) {
    myProcessHandler = processHandler;
    myRunConfiguration = runConfiguration;
    myExecutorId = executorId;
    myExecutionTarget = executionTarget;
  }

  @NotNull
  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  @NotNull
  public String getExecutorId() {
    return myExecutorId;
  }

  @Nullable
  public Executor getExecutor() {
    return ExecutorRegistry.getInstance().getExecutorById(getExecutorId());
  }

  @NotNull
  public ExecutionTarget getExecutionTarget() {
    return myExecutionTarget;
  }

  @Nullable
  public RunConfiguration getRunConfiguration() {
    return myRunConfiguration;
  }

  /**
   * Find all the actively running session in the a given progject.
   */
  @Nullable
  public static List<AndroidSessionInfo> findActiveSession(@NotNull Project project) {
    return Arrays.stream(ExecutionManager.getInstance(project).getRunningProcesses()).map(
      handler -> handler.getUserData(KEY)).filter(Objects::nonNull).collect(Collectors.toList());
  }

  @Nullable
  public static AndroidSessionInfo findOldSession(@NotNull Project project,
                                                  @Nullable Executor executor,
                                                  @NotNull RunConfiguration runConfiguration,
                                                  @NotNull ExecutionTarget executionTarget) {
    // Note: There are 2 alternatives here:
    //    1. RunContentManager.getInstance(project).getAllDescriptors()
    //    2. ExecutionManagerImpl.getInstance(project).getRunningDescriptors
    // The 2nd one doesn't work since its implementation relies on the same run descriptor to be alive as the one that is launched,
    // but that doesn't work for android debug sessions where we have 2 process handlers (one while installing and another while debugging)
    List<AndroidSessionInfo> infos = Arrays.stream(ExecutionManager.getInstance(project).getRunningProcesses())
      .filter(handler -> !handler.isProcessTerminating() && !handler.isProcessTerminated())
      .map(handler -> handler.getUserData(KEY))
      .filter(info -> info != null &&
                      runConfiguration == info.getRunConfiguration() &&
                      (executor == null || executor.getId().equals(info.getExecutorId())))
      .collect(Collectors.toList());

    // There are many scenarios to check here. Given the assumption that:
    // 1) If executionTarget is the DefaultExecutionTarget, then we assume that it's either an old-style run, or a dropdown run with
    //    multiple devices.
    // 2) Otherwise, it should be an AndroidExecutionTarget.
    //
    // Then given the above two scenarios, we have the following cross product of cases [executionTarget x myExecutionTarget]:
    // [AndroidExecutionTarget, AndroidExecutionTarget]: Just check if they are the same.
    // [AndroidExecutionTarget, DefaultExecutionTarget]: Peek into myProcessHandler and check if it contains executionTarget.
    // [DefaultExecutionTarget, AndroidExecutionTarget]: Multi-device launch, so check if myExecutionTarget is contained in executionTarget.
    // [DefaultExecutionTarget, DefaultExecutionTarget]: Multi-device or old-style launch, then just return true (only 1 allowed to exist).
    if (executionTarget instanceof AndroidExecutionTarget) {
      for (AndroidSessionInfo info : infos) {
        ExecutionTarget sessionExecutionTarget = info.getExecutionTarget();
        if (sessionExecutionTarget instanceof AndroidExecutionTarget) {
          if (sessionExecutionTarget.getId().equals(executionTarget.getId())) {
            return info;
          }
        }
        else {
          if (checkIfIDeviceRunningInProcessHandler(runConfiguration, (AndroidExecutionTarget)executionTarget, info.getProcessHandler())) {
            return info;
          }
        }
      }
      return null;
    }
    else {
      for (AndroidSessionInfo info : infos) {
        ExecutionTarget sessionExecutionTarget = info.getExecutionTarget();
        if (sessionExecutionTarget instanceof AndroidExecutionTarget) {
          assert info.myRunConfiguration != null;
          if (checkIfIDeviceRunningInProcessHandler(info.myRunConfiguration, (AndroidExecutionTarget)sessionExecutionTarget,
                                                    info.getProcessHandler())) {
            return info;
          }
        }
        else {
          return info;
        }
      }
      return null;
    }
  }

  private static boolean checkIfIDeviceRunningInProcessHandler(@NotNull RunConfiguration runConfiguration,
                                                               @NotNull AndroidExecutionTarget executionTarget,
                                                               @NotNull ProcessHandler processHandler) {
    if (processHandler instanceof SwappableProcessHandler) {
      return ((SwappableProcessHandler)processHandler).isRunningWith(runConfiguration, executionTarget);
    }

    return false;
  }
}
