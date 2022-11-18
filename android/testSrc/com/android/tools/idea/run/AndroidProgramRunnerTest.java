/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import java.util.Collection;
import java.util.Collections;
import javax.swing.Icon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.mockito.Mockito;

public class AndroidProgramRunnerTest {
  private final FakeExecutionTarget target = new FakeExecutionTarget();
  private final AndroidProgramRunner runner = new AndroidProgramRunner((project, progileState) -> target) {
    @Override
    protected boolean canRunWithMultipleDevices(@NotNull String executorId) {
      return false;
    }

    @Override
    public @NotNull String getRunnerId() {
      return "Fake Runner";
    }
  };
  private final RunConfiguration runConfiguration = Mockito.mock(RunConfiguration.class);

  @Test
  public void ensureCannotRunOnMultipleDevices() {
    target.setAvailableDeviceCount(2);
    assertFalse(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, runConfiguration));
  }

  @Test
  public void ensureCanRunOnNoneOrSingleDevice() {
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
    public @NotNull Collection<@NotNull IDevice> getRunningDevices() {
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
