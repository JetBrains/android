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
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DevicesSelectedServiceTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private PropertiesComponent myProperties;

  @Before
  public void initProperties() {
    myProperties = new ProjectPropertiesComponentImpl();
  }

  @Test
  public void getTargetSelectedWithComboBoxDevicesIsEmpty() {
    // Arrange
    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(),
                                                                project -> myProperties,
                                                                Mockito.mock(Clock.class),
                                                                () -> false);

    // Act
    Object target = service.getTargetSelectedWithComboBox(Collections.emptyList());

    // Assert
    assertEquals(Optional.empty(), target);
  }

  @Test
  public void getTargetSelectedWithComboBoxKeyAsStringIsNull() {
    // Arrange
    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(),
                                                                project -> myProperties,
                                                                Mockito.mock(Clock.class),
                                                                () -> false);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object selectedTarget = service.getTargetSelectedWithComboBox(Collections.singletonList(device));

    // Assert
    assertEquals(Optional.of(new Target(new VirtualDeviceName("Pixel_3_API_29"))), selectedTarget);
  }

  @Test
  public void getTargetSelectedWithComboBoxSelectedDeviceIsntPresent() {
    // Arrange
    myProperties.setValue(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX, "Pixel_2_API_29");

    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(),
                                                                project -> myProperties,
                                                                Mockito.mock(Clock.class),
                                                                () -> false);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object selectedTarget = service.getTargetSelectedWithComboBox(Collections.singletonList(device));

    // Assert
    assertEquals(Optional.of(new Target(new VirtualDeviceName("Pixel_3_API_29"))), selectedTarget);
  }

  @Test
  public void getTargetSelectedWithComboBoxConnectedDeviceIsntPresent() {
    // Arrange
    myProperties.setValue(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX, "Pixel_3_API_29");

    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(),
                                                                project -> myProperties,
                                                                Mockito.mock(Clock.class),
                                                                () -> false);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object selectedTarget = service.getTargetSelectedWithComboBox(Collections.singletonList(device));

    // Assert
    assertEquals(Optional.of(new Target(new VirtualDeviceName("Pixel_3_API_29"))), selectedTarget);
  }

  @Test
  public void getTargetSelectedWithComboBoxSelectionTimeIsBeforeConnectionTime() {
    // Arrange
    myProperties.setValue(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX, "Pixel_3_API_29");
    myProperties.setValue(DevicesSelectedService.TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX, "2018-11-28T01:15:27.000Z");

    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(),
                                                                project -> myProperties,
                                                                Mockito.mock(Clock.class),
                                                                () -> false);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setKey(new VirtualDeviceName("Pixel_2_API_29"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:28.000Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object selectedTarget = service.getTargetSelectedWithComboBox(Arrays.asList(device, connectedDevice));

    // Assert
    assertEquals(Optional.of(new Target(new VirtualDeviceName("Pixel_2_API_29"))), selectedTarget);
  }

  @Test
  public void getTargetSelectedWithComboBox() {
    // Arrange
    myProperties.setValue(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX, "Pixel_3_API_29");
    myProperties.setValue(DevicesSelectedService.TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX, "2018-11-28T01:15:27.000Z");

    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(),
                                                                project -> myProperties,
                                                                Mockito.mock(Clock.class),
                                                                () -> false);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setKey(new VirtualDeviceName("Pixel_2_API_29"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:26.000Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object selectedTarget = service.getTargetSelectedWithComboBox(Arrays.asList(device, connectedDevice));

    // Assert
    assertEquals(Optional.of(new Target(new VirtualDeviceName("Pixel_3_API_29"))), selectedTarget);
  }

  @Test
  public void getTargetSelectedWithComboBoxSelectionTimeIsNull() {
    // Arrange
    myProperties.setValue(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX, "Pixel_3_API_29");

    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(),
                                                                project -> myProperties,
                                                                Mockito.mock(Clock.class),
                                                                () -> false);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setKey(new VirtualDeviceName("Pixel_2_API_29"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:28.000Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object selectedTarget = service.getTargetSelectedWithComboBox(Arrays.asList(device, connectedDevice));

    // Assert
    assertEquals(Optional.of(new Target(new VirtualDeviceName("Pixel_2_API_29"))), selectedTarget);
  }

  @Test
  public void isMultipleDevicesSelectedInComboBoxRunOnMultipleDevicesActionDisabledAndMultipleDevicesNotSelectedInComboBox() {
    // Arrange
    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(),
                                                                project -> myProperties,
                                                                Mockito.mock(Clock.class),
                                                                () -> false);

    // Act
    boolean selected = service.isMultipleDevicesSelectedInComboBox();

    // Assert
    assertFalse(selected);
  }

  @Test
  public void isMultipleDevicesSelectedInComboBoxRunOnMultipleDevicesActionDisabledAndMultipleDevicesSelectedInComboBox() {
    // Arrange
    myProperties.setValue(DevicesSelectedService.MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX, true);

    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(),
                                                                project -> myProperties,
                                                                Mockito.mock(Clock.class),
                                                                () -> false);

    // Act
    boolean selected = service.isMultipleDevicesSelectedInComboBox();

    // Assert
    assertTrue(selected);
  }

  @Test
  public void isMultipleDevicesSelectedInComboBoxRunOnMultipleDevicesActionEnabledAndMultipleDevicesNotSelectedInComboBox() {
    // Arrange
    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(),
                                                                project -> null,
                                                                Mockito.mock(Clock.class),
                                                                () -> true);

    // Act
    boolean selected = service.isMultipleDevicesSelectedInComboBox();

    // Assert
    assertFalse(selected);
  }

  @Test
  public void isMultipleDevicesSelectedInComboBoxRunOnMultipleDevicesActionEnabledAndMultipleDevicesSelectedInComboBox() {
    // Arrange
    myProperties.setValue(DevicesSelectedService.MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX, true);

    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(),
                                                                project -> myProperties,
                                                                Mockito.mock(Clock.class),
                                                                () -> true);

    // Act
    boolean selected = service.isMultipleDevicesSelectedInComboBox();

    // Assert
    assertFalse(selected);
  }

  @Test
  public void setTargetsSelectedWithDialog() {
    // Arrange
    DevicesSelectedService service = new DevicesSelectedService(myRule.getProject(),
                                                                project -> myProperties,
                                                                Mockito.mock(Clock.class),
                                                                () -> false);

    // Act
    service.setTargetsSelectedWithDialog(Collections.emptySet());

    // Assert
    assertTrue(service.isDialogSelectionEmpty());
  }
}
