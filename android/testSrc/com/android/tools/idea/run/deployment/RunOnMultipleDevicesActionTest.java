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
package com.android.tools.idea.run.deployment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class RunOnMultipleDevicesActionTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private AnAction myAction;
  private Presentation myPresentation;
  private AnActionEvent myEvent;

  @Before
  public void mockEvent() {
    myPresentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);
    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
  }

  @Test
  public void updateProjectIsNull() {
    // Arrange
    myAction = new RunOnMultipleDevicesAction();

    // Act
    myAction.update(myEvent);

    // Assert
    assertFalse(myPresentation.isEnabled());
  }

  @Test
  public void updateSettingsAreNull() {
    // Arrange
    myAction = new RunOnMultipleDevicesAction();
    Mockito.when(myEvent.getProject()).thenReturn(myRule.getProject());

    // Act
    myAction.update(myEvent);

    // Assert
    assertFalse(myPresentation.isEnabled());
  }

  @Test
  public void updateWithNonSupportedRunConfigurationTypeShouldDisableAction() {
    // Arrange
    RunnerAndConfigurationSettings settings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(settings.getType()).thenReturn(Mockito.mock(ConfigurationType.class));

    myAction = new RunOnMultipleDevicesAction(project -> settings, project -> Collections.emptyList());
    Mockito.when(myEvent.getProject()).thenReturn(myRule.getProject());

    // Act
    myAction.update(myEvent);

    // Assert
    assertFalse(myPresentation.isEnabled());
  }

  @Test
  public void updateDevicesIsEmpty() {
    // Arrange
    RunnerAndConfigurationSettings settings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(settings.getType()).thenReturn(AndroidRunConfigurationType.getInstance());

    myAction = new RunOnMultipleDevicesAction(project -> settings, project -> Collections.emptyList());
    Mockito.when(myEvent.getProject()).thenReturn(myRule.getProject());

    // Act
    myAction.update(myEvent);

    // Assert
    assertFalse(myPresentation.isEnabled());
  }

  @Test
  public void update() {
    // Arrange
    RunnerAndConfigurationSettings settings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(settings.getType()).thenReturn(AndroidRunConfigurationType.getInstance());

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    myAction = new RunOnMultipleDevicesAction(project -> settings, project -> Collections.singletonList(device));
    Mockito.when(myEvent.getProject()).thenReturn(myRule.getProject());

    // Act
    myAction.update(myEvent);

    // Assert
    assertTrue(myPresentation.isEnabled());
  }
}
