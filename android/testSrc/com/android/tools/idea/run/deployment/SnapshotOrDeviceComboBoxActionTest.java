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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

public final class SnapshotOrDeviceComboBoxActionTest {
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
  public void createPopupActionGroup() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Arrays.asList(
      new VirtualDevice(false, "Pixel 2 XL API 27"),
      new VirtualDevice(false, "Pixel 2 XL API 28"),
      new VirtualDevice(false, "Pixel API 28"),
      new VirtualDevice(false, "Pixel 2 API 27")));

    SnapshotOrDeviceComboBoxAction action = new SnapshotOrDeviceComboBoxAction(() -> true, myDevicesGetter);

    action.update(myEvent);
    Iterator<AnAction> i = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class)).getChildren(null)).iterator();

    assertEquals(new SnapshotActionGroup(new VirtualDevice(false, "Pixel 2 XL API 27"), action), i.next());
    assertEquals(new SnapshotActionGroup(new VirtualDevice(false, "Pixel 2 XL API 28"), action), i.next());
    assertEquals(new SnapshotActionGroup(new VirtualDevice(false, "Pixel API 28"), action), i.next());
    assertEquals(new SnapshotActionGroup(new VirtualDevice(false, "Pixel 2 API 27"), action), i.next());
    assertEquals(Separator.getInstance(), i.next());
    assertTrue(i.next() instanceof RunAndroidAvdManagerAction);
    assertFalse(i.hasNext());
  }

  @Test
  public void createPopupActionGroupActionsIsEmpty() {
    SnapshotOrDeviceComboBoxAction action = new SnapshotOrDeviceComboBoxAction(() -> true, myDevicesGetter);

    action.update(myEvent);
    Iterator<AnAction> i = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class)).getChildren(null)).iterator();

    assertTrue(i.next() instanceof RunAndroidAvdManagerAction);
    assertFalse(i.hasNext());
  }

  @Test
  public void newSnapshotOrDeviceActionsPhysicalDevicesIsEmpty() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(
      new VirtualDevice(false, "Pixel 2 XL API 27")));

    SnapshotOrDeviceComboBoxAction action = new SnapshotOrDeviceComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);

    Object expectedActions = Collections.singletonList(new SnapshotActionGroup(new VirtualDevice(false, "Pixel 2 XL API 27"), action));
    assertEquals(expectedActions, action.newSnapshotOrDeviceActions());
  }

  @Test
  public void newSnapshotOrDeviceActionsVirtualDevicesIsEmpty() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Collections.singletonList(new PhysicalDevice("LGE Nexus 5X")));

    SnapshotOrDeviceComboBoxAction action = new SnapshotOrDeviceComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);

    Object expectedActions = Collections.singletonList(new SelectPhysicalDeviceAction(new PhysicalDevice("LGE Nexus 5X"), action));
    assertEquals(expectedActions, action.newSnapshotOrDeviceActions());
  }

  @Test
  public void newSnapshotOrDeviceActions() {
    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Arrays.asList(
      new VirtualDevice(false, "Pixel 2 XL API 27"),
      new PhysicalDevice("LGE Nexus 5X")));

    SnapshotOrDeviceComboBoxAction action = new SnapshotOrDeviceComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);

    Object expectedActions = Arrays.asList(
      new SnapshotActionGroup(new VirtualDevice(false, "Pixel 2 XL API 27"), action),
      Separator.getInstance(),
      new SelectPhysicalDeviceAction(new PhysicalDevice("LGE Nexus 5X"), action));

    assertEquals(expectedActions, action.newSnapshotOrDeviceActions());
  }

  @Test
  public void updateSelectSnapshotDeviceComboBoxVisibleIsFalse() {
    new SnapshotOrDeviceComboBoxAction(() -> false, myDevicesGetter).update(myEvent);
    assertFalse(myPresentation.isVisible());
  }

  @Test
  public void updateDevicesIsEmpty() {
    SnapshotOrDeviceComboBoxAction action = new SnapshotOrDeviceComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.emptyList(), action.getDevices());
    assertNull(action.getSelectedDevice());
    assertEquals("No devices", myPresentation.getText());
  }

  @Test
  public void update() {
    List<Device> devices = Arrays.asList(
      new VirtualDevice(false, "Pixel 2 XL API 27"),
      new VirtualDevice(false, "Pixel 2 XL API 28"),
      new VirtualDevice(false, "Pixel API 28"),
      new VirtualDevice(false, "Pixel 2 API 27"));

    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(devices);

    SnapshotOrDeviceComboBoxAction action = new SnapshotOrDeviceComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(devices, action.getDevices());
    assertEquals(new VirtualDevice(false, "Pixel 2 XL API 27"), action.getSelectedDevice());
    assertEquals("Pixel 2 XL API 27", myPresentation.getText());
  }

  @Test
  public void updateDevicesDoesntContainSelectedDevice() {
    List<Device> devices = Arrays.asList(
      new VirtualDevice(false, "Pixel 2 XL API 27"),
      new VirtualDevice(false, "Pixel 2 XL API 28"),
      new VirtualDevice(false, "Pixel API 28"));

    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(devices);

    SnapshotOrDeviceComboBoxAction action = new SnapshotOrDeviceComboBoxAction(() -> true, myDevicesGetter);
    action.setSelectedDevice(new VirtualDevice(false, "Pixel 2 API 27"));
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(devices, action.getDevices());
    assertEquals(new VirtualDevice(false, "Pixel 2 XL API 27"), action.getSelectedDevice());
    assertEquals("Pixel 2 XL API 27", myPresentation.getText());
  }

  @Test
  public void updateDisconnectingSelectedDeviceDoesntChangeSelection() {
    SnapshotOrDeviceComboBoxAction action = new SnapshotOrDeviceComboBoxAction(() -> true, myDevicesGetter);
    action.setSelectedDevice(new VirtualDevice(true, "Pixel 2 XL API 28"));

    Mockito.when(myDevicesGetter.get(myRule.getProject())).thenReturn(Arrays.asList(
      new VirtualDevice(false, "Pixel 2 XL API 27"),
      new VirtualDevice(false, "Pixel 2 XL API 28"),
      new VirtualDevice(false, "Pixel API 28"),
      new VirtualDevice(false, "Pixel 2 API 27")));

    action.update(myEvent);
    assertEquals(new VirtualDevice(false, "Pixel 2 XL API 28"), action.getSelectedDevice());
  }
}
