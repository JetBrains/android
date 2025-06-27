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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.AndroidConfigurationExecutor;
import com.android.tools.idea.execution.common.AndroidConfigurationExecutorRunProfileState;
import com.android.tools.idea.execution.common.AndroidConfigurationProgramRunner;
import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.android.tools.idea.execution.common.stats.RunStats;
import com.android.tools.idea.testing.KeepTasksAsynchronousRule;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.ProjectRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.UsefulTestCase;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
  public EdtRule edtRule = new EdtRule();

  @Rule
  public KeepTasksAsynchronousRule keepTasksAsynchronous = new KeepTasksAsynchronousRule(true);


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
                                           @NotNull AndroidConfigurationExecutor executor,
                                           @NotNull ProgressIndicator indicator) {
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
                                           @NotNull AndroidConfigurationExecutor executor,
                                           @NotNull ProgressIndicator indicator) {
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

    UsefulTestCase.assertThrows(java.util.concurrent.ExecutionException.class, "Exception in Runner", () -> execute.get().blockingGet(1000));
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
                                           @NotNull AndroidConfigurationExecutor executor,
                                           @NotNull ProgressIndicator indicator) {
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

  @Test
  @RunsInEdt
  public void fillStatistics() throws ExecutionException, InterruptedException {
    FakeExecutionTarget target = new FakeExecutionTarget();
    AndroidConfigurationProgramRunner runner =
      new AndroidConfigurationProgramRunner((project, profileState) -> target) {
        @NotNull
        @Override
        protected RunContentDescriptor run(@NotNull ExecutionEnvironment environment,
                                           @NotNull AndroidConfigurationExecutor executor,
                                           @NotNull ProgressIndicator indicator) {
          final RunContentDescriptor mock = mock(RunContentDescriptor.class);
          when(mock.getProcessHandler()).thenReturn(mock(ProcessHandler.class));
          return mock;
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
    final RunConfiguration configuration = new RunConfiguration() {
      @Override
      public @Nullable ConfigurationFactory getFactory() {
        return null;
      }

      @Override
      public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return null;
      }

      @Override
      public Project getProject() {
        return projectRule.getProject();
      }

      @Override
      public RunConfiguration clone() {
        return null;
      }

      @Override
      public @Nullable RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment)
        throws ExecutionException {
        final AndroidConfigurationExecutor executor1 = new AndroidConfigurationExecutor() {
          @NotNull
          @Override
          public RunContentDescriptor debug(@NotNull ProgressIndicator indicator) throws ExecutionException {
            return null;
          }

          @NotNull
          @Override
          public RunContentDescriptor run(@NotNull ProgressIndicator indicator) throws ExecutionException {
            return null;
          }

          @NotNull
          @Override
          public RunConfiguration getConfiguration() {
            return null;
          }
        };
        return new AndroidConfigurationExecutorRunProfileState(executor1);
      }

      @Override
      public @NlsSafe
      @NotNull String getName() {
        return "Name";
      }

      @Override
      public void setName(@NlsSafe String name) {

      }

      @Override
      public @Nullable Icon getIcon() {
        return null;
      }
    };

    ExecutionEnvironment env = ExecutionEnvironmentBuilder.create(DefaultDebugExecutor.getDebugExecutorInstance(), configuration)
      .runner(runner)
      .build();
    RunStats stats = mock(RunStats.class);
    final CountDownLatch latch = new CountDownLatch(1);
    doAnswer(mock -> {
      latch.countDown();
      return null;
    }).when(stats).endLaunchTasks();
    env.putUserData(RunStats.KEY, stats);
    runner.execute(env);
    latch.await(10, TimeUnit.SECONDS);
    verify(stats).beginLaunchTasks();
    verify(stats).endLaunchTasks();
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
