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

import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class MultipleDevicesActionTest {
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
    myAction = new MultipleDevicesAction();

    // Act
    myAction.update(myEvent);

    // Assert
    assertFalse(myPresentation.isEnabled());
  }

  @Test
  public void updateSettingsAreNull() {
    // Arrange
    myAction = new MultipleDevicesAction();
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

    DeviceAndSnapshotComboBoxAction comboBoxAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);

    myAction = new MultipleDevicesAction(project -> settings, project -> null, () -> comboBoxAction);
    Mockito.when(myEvent.getProject()).thenReturn(myRule.getProject());

    // Act
    myAction.update(myEvent);

    // Assert
    assertFalse(myPresentation.isEnabled());
  }

  @Test
  public void updateSelectedDevicesIsEmpty() {
    // Arrange
    RunnerAndConfigurationSettings settings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(settings.getType()).thenReturn(AndroidRunConfigurationType.getInstance());

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    Mockito.when(service.isDialogSelectionEmpty()).thenReturn(true);

    DeviceAndSnapshotComboBoxAction comboBoxAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);

    myAction = new MultipleDevicesAction(project -> settings, project -> service, () -> comboBoxAction);
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

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    DeviceAndSnapshotComboBoxAction comboBoxAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);

    myAction = new MultipleDevicesAction(project -> settings, project -> service, () -> comboBoxAction);
    Mockito.when(myEvent.getProject()).thenReturn(myRule.getProject());

    // Act
    myAction.update(myEvent);

    // Assert
    assertTrue(myPresentation.isEnabled());
  }
}
