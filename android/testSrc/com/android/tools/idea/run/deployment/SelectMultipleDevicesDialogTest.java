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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.ide.util.ProjectPropertiesComponentImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.swing.Action;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SelectMultipleDevicesDialogTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private SelectMultipleDevicesDialog myDialog;

  private void initDialog(@NotNull BooleanSupplier runOnMultipleDevicesActionEnabledGet,
                          @NotNull TableModel tableModel,
                          @NotNull Function<@NotNull Project, @NotNull DevicesSelectedService> devicesSelectedServiceGetInstance) {
    ApplicationManager.getApplication().invokeAndWait(() -> myDialog = new SelectMultipleDevicesDialog(myRule.getProject(),
                                                                                                       runOnMultipleDevicesActionEnabledGet,
                                                                                                       tableModel,
                                                                                                       devicesSelectedServiceGetInstance));
  }

  @After
  public void disposeOfDialog() {
    ApplicationManager.getApplication().invokeAndWait(myDialog::disposeIfNeeded);
  }

  @Test
  public void selectMultipleDevicesDialogRunOnMultipleDevicesActionIsEnabled() {
    // Arrange
    TableModel model = Mockito.mock(TableModel.class);
    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);

    // Act
    initDialog(() -> true, model, project -> service);

    // Assert
    assertEquals("Run on Multiple Devices", myDialog.getTitle());
  }

  @Test
  public void selectMultipleDevicesDialog() {
    // Arrange
    TableModel model = Mockito.mock(TableModel.class);
    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);

    // Act
    initDialog(() -> false, model, project -> service);

    // Assert
    assertEquals("Select Multiple Devices", myDialog.getTitle());
  }

  @Test
  public void initTableRunOnMultipleDevicesActionIsEnabled() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(new VirtualDeviceName("Pixel_4_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    initDialog(() -> true, new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device)), project -> service);

    // Act
    myDialog.getTable().setSelected(true, 0);

    // Assert
    assertTrue(myDialog.getOKAction().isEnabled());
  }

  @Test
  public void initTable() {
    // Arrange
    Key key = new VirtualDeviceName("Pixel_4_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    PropertiesComponent properties = new ProjectPropertiesComponentImpl();
    Clock clock = Clock.fixed(Instant.parse("2018-11-28T01:15:27Z"), ZoneId.of("America/Los_Angeles"));

    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(), project -> properties, clock, () -> false);
    service.setDeviceKeysSelectedWithDialog(Collections.singleton(key));

    initDialog(() -> false, new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device)), project -> service);

    // Act
    myDialog.getTable().setSelected(false, 0);

    // Assert
    assertTrue(myDialog.getOKAction().isEnabled());
  }

  @Test
  public void initOkAction() {
    // Arrange
    TableModel model = Mockito.mock(TableModel.class);
    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);

    // Act
    initDialog(() -> true, model, project -> service);

    // Assert
    Action action = myDialog.getOKAction();

    assertFalse(action.isEnabled());
    assertEquals("Run", action.getValue(Action.NAME));
  }

  @Test
  public void initOkActionDoesntDisableActionAfterTableModelEventsAreHandled() {
    // Arrange
    Key key = new VirtualDeviceName("Pixel_4_API_30");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    TableModel model = new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device));

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    Mockito.when(service.getDeviceKeysSelectedWithDialog()).thenReturn(Collections.singleton(key));

    // Act
    initDialog(() -> true, model, project -> service);

    // Assert
    assertTrue(myDialog.getOKAction().isEnabled());
  }
}
