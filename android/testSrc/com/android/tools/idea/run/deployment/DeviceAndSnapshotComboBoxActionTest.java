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

import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import java.time.Clock;
import java.time.Instant;
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
  private Clock myClock;

  private Project myProject;
  private Presentation myPresentation;
  private AnActionEvent myEvent;

  private DataContext myContext;

  @Before
  public void mockDevicesGetter() {
    myDevicesGetter = Mockito.mock(AsyncDevicesGetter.class);
  }

  @Before
  public void mockClock() {
    myClock = Mockito.mock(Clock.class);
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
  public void getSelectedDeviceDevicesIsEmpty() {
    assertNull(new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock).getSelectedDevice(myProject));
  }

  @Test
  public void getSelectedDeviceSelectedDeviceIsntPresent() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);

    assertEquals(builder.build(), action.getSelectedDevice(myProject));
  }

  @Test
  public void getSelectedDeviceSelectedDeviceIsConnected() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setConnected(true)
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    action.setSelectedDevice(myProject, builder.build());

    assertEquals(builder.build(), action.getSelectedDevice(myProject));
  }

  @Test
  public void getSelectedDeviceSelectionTimeIsBeforeConnectionTime() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    Mockito.when(myClock.instant()).thenReturn(Instant.parse("2018-11-28T01:15:27.000Z"));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    action.setSelectedDevice(myProject, builder.build());

    Device physicalDevice = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey("00fff9d2279fa601")
      .setConnectionTime(Instant.parse("2018-11-28T01:15:28.000Z"))
      .build();

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Arrays.asList(builder.build(), physicalDevice));

    action.update(myEvent);

    assertEquals(physicalDevice, action.getSelectedDevice(myProject));
  }

  @Test
  public void getSelectedDeviceConnectedDeviceIsntPresent() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    action.setSelectedDevice(myProject, builder.build());

    Device device = new VirtualDevice.Builder()
      .setName("Pixel XL API 28")
      .setKey("Pixel_XL_API_28")
      .setSnapshots(ImmutableList.of())
      .build();

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Arrays.asList(builder.build(), device));

    action.update(myEvent);

    assertEquals(builder.build(), action.getSelectedDevice(myProject));
  }

  @Test
  public void createPopupActionGroupActionsIsEmpty() {
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    assertEquals(actualChildren, Collections.singletonList(action.getOpenAvdManagerAction()));
  }

  @Test
  public void createPopupActionGroupDeviceIsVirtual() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
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
    Device.Builder builder = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey("00fff9d2279fa601");

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
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
  public void createPopupActionGroupDevicesAreVirtualAndPhysical() {
    Device.Builder virtualDeviceBuilder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Device.Builder physicalDeviceBuilder = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey("00fff9d2279fa601");

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Arrays.asList(virtualDeviceBuilder.build(), physicalDeviceBuilder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(physicalDeviceBuilder.build())
        .build(),
      Separator.getInstance(),
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(virtualDeviceBuilder.build())
        .build(),
      Separator.getInstance(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDeviceSnapshotIsDefault() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
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
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of("snap_2018-08-07_16-27-58"));

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> false, myDevicesGetter, myClock);
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
  public void updateSelectDeviceSnapshotComboBoxVisibleIsFalse() {
    new DeviceAndSnapshotComboBoxAction(() -> false, () -> false, myDevicesGetter, myClock).update(myEvent);
    assertFalse(myPresentation.isVisible());
  }

  @Test
  public void updateDevicesIsEmpty() {
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
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
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
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
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
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
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    Device device = new VirtualDevice.Builder()
      .setName("Pixel XL API 28")
      .setKey("Pixel_XL_API_28")
      .setSnapshots(ImmutableList.of())
      .build();

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
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
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
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
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
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
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
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
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setSnapshots(ImmutableList.of());

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(builder.build()));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
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
