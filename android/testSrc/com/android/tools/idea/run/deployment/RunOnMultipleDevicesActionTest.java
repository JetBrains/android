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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jetbrains.android.util.AndroidBuildCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class RunOnMultipleDevicesActionTest {
  private final Presentation myPresentation = new Presentation();

  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  @Test
  public void updateProjectIsNull() {
    // Arrange
    RunManager manager = mockRunManager(AndroidRunConfigurationType.ID);
    AsyncDevicesGetter getter = mockAsyncDevicesGetter(Collections.emptyList());

    AnAction action = new RunOnMultipleDevicesAction(project -> manager, project -> getter, () -> false);
    AnActionEvent event = mockEvent(myPresentation, null);

    // Act
    action.update(event);

    // Assert
    assertFalse(myPresentation.isEnabledAndVisible());
  }

  @Test
  public void updateConfigurationAndSettingsIsNull() {
    // Arrange
    RunManager manager = Mockito.mock(RunManager.class);
    AsyncDevicesGetter getter = mockAsyncDevicesGetter(Collections.emptyList());

    AnAction action = new RunOnMultipleDevicesAction(project -> manager, project -> getter, () -> false);
    AnActionEvent event = mockEvent(myPresentation, myRule.getProject());

    // Act
    action.update(event);

    // Assert
    assertFalse(myPresentation.isEnabled());
  }

  @Test
  public void updateCaseAndroidTestRunConfigurationType() {
    // Arrange
    RunManager manager = mockRunManager(AndroidBuildCommonUtils.ANDROID_TEST_RUN_CONFIGURATION_TYPE);
    AsyncDevicesGetter getter = mockAsyncDevicesGetter(Collections.emptyList());

    AnAction action = new RunOnMultipleDevicesAction(project -> manager, project -> getter, () -> false);
    AnActionEvent event = mockEvent(myPresentation, myRule.getProject());

    // Act
    action.update(event);

    // Assert
    assertFalse(myPresentation.isEnabled());
  }

  @Test
  public void updateDefault() {
    // Arrange
    RunManager manager = mockRunManager("AndroidJUnit");
    AsyncDevicesGetter getter = mockAsyncDevicesGetter(Collections.emptyList());

    AnAction action = new RunOnMultipleDevicesAction(project -> manager, project -> getter, () -> false);
    AnActionEvent event = mockEvent(myPresentation, myRule.getProject());

    // Act
    action.update(event);

    // Assert
    assertFalse(myPresentation.isEnabled());
  }

  @Test
  public void updateDevicesIsEmpty() {
    // Arrange
    RunManager manager = mockRunManager(AndroidRunConfigurationType.ID);
    AsyncDevicesGetter getter = mockAsyncDevicesGetter(Collections.emptyList());

    AnAction action = new RunOnMultipleDevicesAction(project -> manager, project -> getter, () -> false);
    AnActionEvent event = mockEvent(myPresentation, myRule.getProject());

    // Act
    action.update(event);

    // Assert
    assertFalse(myPresentation.isEnabled());
  }

  @Test
  public void update() {
    // Arrange
    RunManager manager = mockRunManager(AndroidRunConfigurationType.ID);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(new VirtualDeviceName("Pixel_4_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    AsyncDevicesGetter getter = mockAsyncDevicesGetter(Collections.singletonList(device));

    AnAction action = new RunOnMultipleDevicesAction(project -> manager, project -> getter, () -> false);
    AnActionEvent event = mockEvent(myPresentation, myRule.getProject());

    // Act
    action.update(event);

    // Assert
    assertTrue(myPresentation.isEnabled());
  }

  private static @NotNull RunManager mockRunManager(@NotNull String id) {
    ConfigurationType type = Mockito.mock(ConfigurationType.class);
    Mockito.when(type.getId()).thenReturn(id);

    RunnerAndConfigurationSettings configurationAndSettings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(configurationAndSettings.getType()).thenReturn(type);

    RunManager manager = Mockito.mock(RunManager.class);
    Mockito.when(manager.getSelectedConfiguration()).thenReturn(configurationAndSettings);

    return manager;
  }

  private static @NotNull AsyncDevicesGetter mockAsyncDevicesGetter(@NotNull List<@NotNull Device> devices) {
    AsyncDevicesGetter getter = Mockito.mock(AsyncDevicesGetter.class);
    Mockito.when(getter.get()).thenReturn(Optional.of(devices));

    return getter;
  }

  private static @NotNull AnActionEvent mockEvent(@NotNull Presentation presentation, @Nullable Project project) {
    AnActionEvent event = Mockito.mock(AnActionEvent.class);
    Mockito.when(event.getPresentation()).thenReturn(presentation);

    if (project != null) {
      Mockito.when(event.getProject()).thenReturn(project);
    }

    return event;
  }
}
