/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.JComponent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class DeviceAndSnapshotComboBoxActionTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private AsyncDevicesGetter myDevicesGetter;

  private Project myProject;
  private Presentation myPresentation;
  private AnActionEvent myEvent;

  private DataContext myContext;

  @Before
  public void mockDevicesGetter() {
    myDevicesGetter = Mockito.mock(AsyncDevicesGetter.class);
  }

  @Before
  public void mockEvent() {
    myProject = myRule.getProject();
    myPresentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);

    Mockito.when(myEvent.getProject()).thenReturn(myProject);
    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
  }

  @Before
  public void mockContext() {
    myContext = Mockito.mock(DataContext.class);
    Mockito.when(myContext.getData(CommonDataKeys.PROJECT)).thenReturn(myRule.getProject());
  }

  @Test
  public void createPopupActionGroupActionsIsEmpty() {
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    assertEquals(actualChildren, Collections.singletonList(action.getOpenAvdManagerAction()));
  }

  @Test
  public void createPopupActionGroupDeviceIsVirtual() {
    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(builder.build())
        .build(),
      Separator.getInstance(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDeviceIsPhysical() {
    IDevice ddmlibDevice = Mockito.mock(IDevice.class);
    Mockito.when(ddmlibDevice.getSerialNumber()).thenReturn("00fff9d2279fa601");

    Device physicalDevice = new PhysicalDevice(new DeviceNameProperties(null, null, null, null), ddmlibDevice);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(physicalDevice));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(new PhysicalDevice(new DeviceNameProperties(null, null, null, null), ddmlibDevice))
        .build(),
      Separator.getInstance(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDevicesAreVirtualAndPhysical() {
    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    IDevice ddmlibDevice = Mockito.mock(IDevice.class);
    Mockito.when(ddmlibDevice.getSerialNumber()).thenReturn("00fff9d2279fa601");

    Device physicalDevice = new PhysicalDevice(new DeviceNameProperties(null, null, null, null), ddmlibDevice);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Arrays.asList(builder.build(), physicalDevice));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(builder.build())
        .build(),
      Separator.getInstance(),
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(new PhysicalDevice(new DeviceNameProperties(null, null, null, null), ddmlibDevice))
        .build(),
      Separator.getInstance(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDeviceSnapshotIsDefault() {
    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(builder.build())
        .setSnapshot(VirtualDevice.DEFAULT_SNAPSHOT)
        .build(),
      Separator.getInstance(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupSnapshotIsntDefault() {
    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of("snap_2018-08-07_16-27-58"));

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SnapshotActionGroup(builder.build(), action, myProject),
      Separator.getInstance(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void updateSelectDeviceSnapshotComboBoxVisibleIsFalse() {
    new DeviceAndSnapshotComboBoxAction(() -> false, () -> false, myDevicesGetter).update(myEvent);
    assertFalse(myPresentation.isVisible());
  }

  @Test
  public void updateDevicesIsEmpty() {
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.emptyList(), action.getDevices());
    assertNull(action.getSelectedDevice(myProject));
    assertNull(action.getSelectedSnapshot());
    assertNull(myPresentation.getIcon());
    assertEquals("No devices", myPresentation.getText());
  }

  @Test
  public void updateSelectedDeviceIsNull() {
    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build()), action.getDevices());
    assertEquals(builder.build(), action.getSelectedDevice(myProject));
    assertNull(action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28, myPresentation.getText());
  }

  @Test
  public void updateDevicesContainSelectedDevice() {
    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.setSelectedDevice(myProject, builder.build());
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build()), action.getDevices());
    assertEquals(builder.build(), action.getSelectedDevice(myProject));
    assertNull(action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28, myPresentation.getText());
  }

  @Test
  public void updateDevicesDontContainSelectedDevice() {
    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    Device device = new VirtualDevice.Builder()
      .setName("Pixel XL API 28")
      .setKey("Pixel_XL_API_28")
      .setSnapshots(ImmutableList.of())
      .build();

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.setSelectedDevice(myProject, device);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build()), action.getDevices());
    assertEquals(builder.build(), action.getSelectedDevice(myProject));
    assertNull(action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28, myPresentation.getText());
  }

  @Test
  public void updateSelectedSnapshotIsNull() {
    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.setSelectedDevice(myProject, builder.build());
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build()), action.getDevices());
    assertEquals(builder.build(), action.getSelectedDevice(myProject));
    assertEquals(VirtualDevice.DEFAULT_SNAPSHOT, action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28 + " - defaultboot", myPresentation.getText());
  }

  @Test
  public void updateSnapshotsContainSelectedSnapshot() {
    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.setSelectedDevice(myProject, builder.build());
    action.setSelectedSnapshot(VirtualDevice.DEFAULT_SNAPSHOT);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build()), action.getDevices());
    assertEquals(builder.build(), action.getSelectedDevice(myProject));
    assertEquals(VirtualDevice.DEFAULT_SNAPSHOT, action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28 + " - defaultboot", myPresentation.getText());
  }

  @Test
  public void updateSelectedDeviceHasSnapshot() {
    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.setSelectedDevice(myProject, builder.build());
    action.setSelectedSnapshot("snap_2018-08-07_16-27-58");
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build()), action.getDevices());
    assertEquals(builder.build(), action.getSelectedDevice(myProject));
    assertEquals(VirtualDevice.DEFAULT_SNAPSHOT, action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28 + " - defaultboot", myPresentation.getText());
  }

  @Test
  public void updateSelectedDeviceDoesntHaveSnapshot() {
    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter);
    action.setSelectedDevice(myProject, builder.build());
    action.setSelectedSnapshot(VirtualDevice.DEFAULT_SNAPSHOT);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build()), action.getDevices());
    assertEquals(builder.build(), action.getSelectedDevice(myProject));
    assertNull(action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28, myPresentation.getText());
  }
}
