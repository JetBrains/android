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
package com.android.tools.idea.run.deployment;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import java.time.Instant;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ExecutionTargetServiceTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  @Test
  public void setActiveTarget() {
    // Arrange
    ExecutionTargetManager executionTargetManager = new FakeExecutionTargetManager();

    RunConfiguration configuration = Mockito.mock(RunConfiguration.class);

    RunnerAndConfigurationSettings configurationAndSettings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(configurationAndSettings.getConfiguration()).thenReturn(configuration);

    RunManager runManager = Mockito.mock(RunManager.class);
    Mockito.when(runManager.getSelectedConfiguration()).thenReturn(configurationAndSettings);
    Mockito.when(runManager.findSettings(configuration)).thenReturn(configurationAndSettings);

    ExecutionTargetService executionTargetService =
      new ExecutionTargetService(myRule.getProject(), project -> executionTargetManager, project -> runManager);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(new Key("Pixel_4_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    DeviceAndSnapshotComboBoxExecutionTarget target1 = new DeviceAndSnapshotComboBoxExecutionTarget(Collections.singletonList(device));

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(new Key("Pixel_4_API_29"))
      .setConnectionTime(Instant.parse("2020-03-13T23:13:20.913Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    DeviceAndSnapshotComboBoxExecutionTarget target2 =
      new DeviceAndSnapshotComboBoxExecutionTarget(Collections.singletonList(connectedDevice));

    // Act
    executionTargetService.setActiveTarget(target1);
    executionTargetService.setActiveTarget(target2);

    // Assert
    assertEquals(target2, executionTargetManager.getActiveTarget());
  }
}
