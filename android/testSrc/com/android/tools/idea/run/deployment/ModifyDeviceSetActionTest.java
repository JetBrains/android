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

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import java.time.Clock;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ModifyDeviceSetActionTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  @Test
  public void actionPerformed() {
    // Arrange
    PropertiesComponent properties = Mockito.mock(PropertiesComponent.class);
    Mockito.when(properties.getBoolean(DeviceAndSnapshotComboBoxAction.MULTIPLE_DEVICES_SELECTED)).thenReturn(true);

    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setKey(new Key("Pixel_2_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    RunConfiguration configuration = Mockito.mock(RunConfiguration.class);

    RunnerAndConfigurationSettings configurationAndSettings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(configurationAndSettings.getConfiguration()).thenReturn(configuration);

    RunManager runManager = Mockito.mock(RunManager.class);

    Mockito.when(runManager.getSelectedConfiguration()).thenReturn(configurationAndSettings);
    Mockito.when(runManager.findSettings(configuration)).thenReturn(configurationAndSettings);

    ExecutionTargetManager executionTargetManager = Mockito.mock(ExecutionTargetManager.class);
    Mockito.when(executionTargetManager.getActiveTarget()).thenReturn(new DeviceAndSnapshotComboBoxExecutionTarget(device1));

    DeviceAndSnapshotComboBoxAction comboBoxAction = new DeviceAndSnapshotComboBoxAction.Builder()
      .setGetProperties(project -> properties)
      .setClock(Mockito.mock(Clock.class))
      .setGetSelectedDevices(project -> Arrays.asList(device1, device2))
      .setGetRunManager(project -> runManager)
      .setGetExecutionTargetManager(project -> executionTargetManager)
      .build();

    DialogWrapper dialog = Mockito.mock(DialogWrapper.class);
    Mockito.when(dialog.showAndGet()).thenReturn(true);

    AnAction action = new ModifyDeviceSetAction(comboBoxAction, project -> dialog);

    AnActionEvent event = Mockito.mock(AnActionEvent.class);
    Mockito.when(event.getProject()).thenReturn(myRule.getProject());

    // Act
    action.actionPerformed(event);

    // Assert
    Mockito.verify(executionTargetManager).getActiveTarget();
    Mockito.verify(executionTargetManager).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(Arrays.asList(device1, device2)));
  }
}
