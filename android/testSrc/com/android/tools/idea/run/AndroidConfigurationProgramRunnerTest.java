/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner;
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor;
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutorRunProfileState;
import com.android.tools.idea.testing.KeepTasksAsynchronousRule;
import com.google.common.truth.Truth;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.testFramework.ProjectRule;
import com.intellij.testFramework.UsefulTestCase;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Icon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.junit.Rule;
import org.junit.Test;

public class AndroidConfigurationProgramRunnerTest {

  @Rule
  public ProjectRule projectRule = new ProjectRule();

  @Rule
  public KeepTasksAsynchronousRule keepTasksAsynchronous = new KeepTasksAsynchronousRule(true);

  /**
   * {@link HiddenRunContentDescriptor} is a almost-pure wrapper class for
   * {@link RunContentDescriptor}, with the excDefaultStudioProgramRunnerTesteption of the {@link RunContentDescriptor#isHiddenContent()} method overridden to return
   * {@code false}. All other methods in the wrapper class should be overrides to the base class (with the addition of
   * {@link com.intellij.openapi.Disposable} handling.
   * <p>
   * This test is to ensure that all public and protected methods of the base class are overridden by the deriving class, and should break
   * if the base class has methods added to it due to IJ merges (in which case, just override the newly added method with proper disposal
   * handling). All other cases should result in compiler errors (either stale {@link Override} or mismatched signatures).
   */
  @Test
  public void ensureAllPublicProtectedMethodsAreOverridden() {
    long runContentDescriptorMethodCount = Arrays.stream(RunContentDescriptor.class.getDeclaredMethods())
      .filter(method -> {
        int modifier = method.getModifiers();
        return Modifier.isPublic(modifier) || Modifier.isProtected(modifier);
      })
      .count();
    long hiddenRunContentDescriptorMethodCount =
      Arrays.stream(HiddenRunContentDescriptor.class.getDeclaredMethods())
        .filter(method -> {
          int modifier = method.getModifiers();
          return Modifier.isPublic(modifier) || Modifier.isProtected(modifier);
        })
        .count();
    Truth.assertThat(runContentDescriptorMethodCount).isEqualTo(hiddenRunContentDescriptorMethodCount);
  }

  @Test
  public void ensureCannotRunOnMultipleDevices() {
    AndroidRunConfiguration runConfiguration =
      new AndroidRunConfiguration(projectRule.getProject(), AndroidRunConfigurationType.getInstance()
        .getFactory());
    FakeExecutionTarget target = new FakeExecutionTarget();
    AndroidConfigurationProgramRunner runner =
      new AndroidConfigurationProgramRunner((project, profileState) -> target) {
        @NotNull
        @Override
        protected List<String> getSupportedConfigurationTypeIds() {
          return List.of(new AndroidRunConfigurationType().getId());
        }

        @Override
        protected boolean canRunWithMultipleDevices(@NotNull String executorId) {
          return false;
        }

        @Override
        public @NotNull String getRunnerId() {
          return "Fake Runner";
        }

        @NotNull
        @Override
        protected RunContentDescriptor run(@NotNull ExecutionEnvironment environment,
                                           @NotNull RunProfileState state,
                                           @NotNull ProgressIndicator indicator) throws ExecutionException {
          return mock(RunContentDescriptor.class);
        }
      };
    target.setAvailableDeviceCount(2);
    assertFalse(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, runConfiguration));
  }

  @Test
  public void resolvePromiseEvenIfUncheckedExceptionHappened() {
    RunnerAndConfigurationSettings runConfiguration =
      RunManager.getInstance(projectRule.getProject()).createConfiguration("app", AndroidRunConfigurationType.getInstance().getFactory());
    FakeExecutionTarget target = new FakeExecutionTarget();
    AndroidConfigurationProgramRunner runner =
      new AndroidConfigurationProgramRunner((project, profileState) -> target) {
        @NotNull
        @Override
        protected List<String> getSupportedConfigurationTypeIds() {
          return List.of(new AndroidRunConfigurationType().getId());
        }

        @Override
        protected boolean canRunWithMultipleDevices(@NotNull String executorId) {
          return false;
        }

        @Override
        public @NotNull String getRunnerId() {
          return "Fake Runner";
        }

        @NotNull
        @Override
        protected RunContentDescriptor run(@NotNull ExecutionEnvironment environment,
                                           @NotNull RunProfileState state,
                                           @NotNull ProgressIndicator indicator) throws ExecutionException {
          throw new RuntimeException("Exception in Runner");
        }
      };
    ExecutionEnvironment env =
      new ExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance(), runner, runConfiguration, projectRule.getProject());
    AtomicReference<Promise<RunContentDescriptor>> execute = new AtomicReference<>();

    ApplicationManager.getApplication().invokeAndWait(() ->
                                                      {
                                                        try {
                                                          execute.set(runner.execute(env, new AndroidConfigurationExecutorRunProfileState(
                                                            mock(AndroidConfigurationExecutor.class))));
                                                        }
                                                        catch (ExecutionException e) {
                                                          throw new RuntimeException(e);
                                                        }
                                                      });

    UsefulTestCase.assertThrows(RuntimeException.class, "Exception in Runner", () -> execute.get().blockingGet(1000));
    assertTrue(Promises.isRejected(execute.get()));
  }

  @Test
  public void ensureCanRunOnNoneOrSingleDevice() {
    AndroidRunConfiguration runConfiguration =
      new AndroidRunConfiguration(projectRule.getProject(), AndroidRunConfigurationType.getInstance().getFactory());
    FakeExecutionTarget target = new FakeExecutionTarget();
    AndroidConfigurationProgramRunner runner =
      new AndroidConfigurationProgramRunner((project, profileState) -> target) {
        @NotNull
        @Override
        protected RunContentDescriptor run(@NotNull ExecutionEnvironment environment,
                                           @NotNull RunProfileState state,
                                           @NotNull ProgressIndicator indicator) throws ExecutionException {
          return mock(RunContentDescriptor.class);
        }

        @NotNull
        @Override
        protected List<String> getSupportedConfigurationTypeIds() {
          return List.of(new AndroidRunConfigurationType().getId());
        }

        @Override
        protected boolean canRunWithMultipleDevices(@NotNull String executorId) {
          return false;
        }

        @Override
        public @NotNull String getRunnerId() {
          return "Fake Runner";
        }
      };
    target.setAvailableDeviceCount(0);

    assertTrue(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, runConfiguration));
    target.setAvailableDeviceCount(1);
    assertTrue(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, runConfiguration));
  }

  private static class FakeExecutionTarget extends AndroidExecutionTarget {
    private int myAvailableDeviceCount;

    private FakeExecutionTarget() {
    }

    @Override
    public int getAvailableDeviceCount() {
      return myAvailableDeviceCount;
    }

    private void setAvailableDeviceCount(int availableDeviceCount) {
      myAvailableDeviceCount = availableDeviceCount;
    }

    @Override
    public boolean isApplicationRunning(@NotNull String packageName) {
      return false;
    }

    @Override
    public @NotNull Collection<IDevice> getRunningDevices() {
      return Collections.emptyList();
    }

    @Override
    public @NotNull String getId() {
      return "Fake Execution Target";
    }

    @Override
    public @NotNull @Nls String getDisplayName() {
      return "Fake Execution Target";
    }

    @Override
    public @Nullable Icon getIcon() {
      return null;
    }
  }
}
