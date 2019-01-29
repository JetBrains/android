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

import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import icons.AndroidIcons;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public final class DeviceAndSnapshotComboBoxActionTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private AsyncDevicesGetter myDevicesGetter;

  private Presentation myPresentation;
  private AnActionEvent myEvent;

  @Before
  public void mockDevicesGetter() {
    myDevicesGetter = Mockito.mock(AsyncDevicesGetter.class);
  }

  @Before
  public void mockEvent() {
    myPresentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);
    Mockito.when(myEvent.getProject()).thenReturn(myRule.getProject());
    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
  }

  @Test
  public void createPopupActionGroupActionsIsEmpty() {
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class)).getChildren(null));

    assertEquals(actualChildren, Collections.singletonList(action.getOpenAvdManagerAction()));
  }

  @Test
  public void createPopupActionGroupDeviceIsVirtual() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28)));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class)).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction(action, new VirtualDevice(false, Devices.PIXEL_2_XL_API_28)),
      Separator.getInstance(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDeviceIsPhysical() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(new PhysicalDevice(Devices.LGE_NEXUS_5X)));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class)).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction(action, new PhysicalDevice(Devices.LGE_NEXUS_5X)),
      Separator.getInstance(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDevicesAreVirtualAndPhysical() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Arrays.asList(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28),
      new PhysicalDevice(Devices.LGE_NEXUS_5X)));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class)).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction(action, new VirtualDevice(false, Devices.PIXEL_2_XL_API_28)),
      Separator.getInstance(),
      new SelectDeviceAndSnapshotAction(action, new PhysicalDevice(Devices.LGE_NEXUS_5X)),
      Separator.getInstance(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDeviceSnapshotIsDefault() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION)));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class)).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction(
        action,
        new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION),
        VirtualDevice.DEFAULT_SNAPSHOT),
      Separator.getInstance(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupSnapshotIsntDefault() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, ImmutableList.of("snap_2018-08-07_16-27-58"))));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class)).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SnapshotActionGroup(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, ImmutableList.of("snap_2018-08-07_16-27-58")), action),
      Separator.getInstance(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void updateSelectDeviceSnapshotComboBoxVisibleIsFalse() {
    new DeviceAndSnapshotComboBoxAction(() -> false, myDevicesGetter).update(myEvent);
    assertFalse(myPresentation.isVisible());
  }

  @Test
  public void updateDevicesIsEmpty() {
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.emptyList(), action.getDevices());
    assertNull(action.getSelectedDevice());
    assertNull(action.getSelectedSnapshot());
    assertNull(myPresentation.getIcon());
    assertEquals("No devices", myPresentation.getText());
  }

  @Test
  public void updateSelectedDeviceIsNull() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28)));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28)), action.getDevices());
    assertEquals(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28), action.getSelectedDevice());
    assertNull(action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28, myPresentation.getText());
  }

  @Test
  public void updateDevicesContainSelectedDevice() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28)));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.setSelectedDevice(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28));
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28)), action.getDevices());
    assertEquals(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28), action.getSelectedDevice());
    assertNull(action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28, myPresentation.getText());
  }

  @Test
  public void updateDevicesDontContainSelectedDevice() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28)));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.setSelectedDevice(new VirtualDevice(false, "Pixel XL API 28"));
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28)), action.getDevices());
    assertEquals(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28), action.getSelectedDevice());
    assertNull(action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28, myPresentation.getText());
  }

  @Test
  public void updateSelectedSnapshotIsNull() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION)));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.setSelectedDevice(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION));
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(
      Collections.singletonList(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION)),
      action.getDevices());
    assertEquals(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION),
      action.getSelectedDevice());
    assertEquals(VirtualDevice.DEFAULT_SNAPSHOT, action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28 + " - defaultboot", myPresentation.getText());
  }

  @Test
  public void updateSnapshotsContainSelectedSnapshot() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION)));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.setSelectedDevice(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION));
    action.setSelectedSnapshot(VirtualDevice.DEFAULT_SNAPSHOT);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(
      Collections.singletonList(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION)),
      action.getDevices());
    assertEquals(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION),
      action.getSelectedDevice());
    assertEquals(VirtualDevice.DEFAULT_SNAPSHOT, action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28 + " - defaultboot", myPresentation.getText());
  }

  @Test
  public void updateSelectedDeviceHasSnapshot() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION)));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.setSelectedDevice(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION));
    action.setSelectedSnapshot("snap_2018-08-07_16-27-58");
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(
      Collections.singletonList(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION)),
      action.getDevices());
    assertEquals(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION),
      action.getSelectedDevice());
    assertEquals(VirtualDevice.DEFAULT_SNAPSHOT, action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28 + " - defaultboot", myPresentation.getText());
  }

  @Test
  public void updateSelectedDeviceDoesntHaveSnapshot() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(
      new VirtualDevice(false, Devices.PIXEL_2_XL_API_28)));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, myDevicesGetter);
    action.setSelectedDevice(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28));
    action.setSelectedSnapshot(VirtualDevice.DEFAULT_SNAPSHOT);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28)), action.getDevices());
    assertEquals(new VirtualDevice(false, Devices.PIXEL_2_XL_API_28), action.getSelectedDevice());
    assertNull(action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28, myPresentation.getText());
  }
}
