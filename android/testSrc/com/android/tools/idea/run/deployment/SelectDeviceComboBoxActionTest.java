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
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import static org.junit.Assert.*;

public final class SelectDeviceComboBoxActionTest {
  @Rule
  public final TestRule myRule = AndroidProjectRule.inMemory();

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
    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
  }

  @Test
  public void createPopupActionGroup() {
    Mockito.when(myDevicesGetter.get()).thenReturn(Arrays.asList(
      "Pixel 2 XL API 27",
      "Pixel 2 XL API 28",
      "Pixel API 28",
      "Pixel 2 API 27"));

    SelectDeviceComboBoxAction action = new SelectDeviceComboBoxAction(() -> true, myDevicesGetter);

    action.update(myEvent);
    Iterator<AnAction> i = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class)).getChildren(null)).iterator();

    assertEquals(new SelectDeviceAction("Pixel 2 XL API 27", action), i.next());
    assertEquals(new SelectDeviceAction("Pixel 2 XL API 28", action), i.next());
    assertEquals(new SelectDeviceAction("Pixel API 28", action), i.next());
    assertEquals(new SelectDeviceAction("Pixel 2 API 27", action), i.next());
    assertEquals(Separator.getInstance(), i.next());
    assertTrue(i.next() instanceof RunAndroidAvdManagerAction);
    assertFalse(i.hasNext());
  }

  @Test
  public void createPopupActionGroupActionsIsEmpty() {
    SelectDeviceComboBoxAction action = new SelectDeviceComboBoxAction(() -> true, myDevicesGetter);

    action.update(myEvent);
    Iterator<AnAction> i = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class)).getChildren(null)).iterator();

    assertTrue(i.next() instanceof RunAndroidAvdManagerAction);
    assertFalse(i.hasNext());
  }

  @Test
  public void updateSelectDeviceComboBoxActionVisibleIsFalse() {
    new SelectDeviceComboBoxAction(() -> false, myDevicesGetter).update(myEvent);
    assertFalse(myPresentation.isVisible());
  }

  @Test
  public void updateDevicesIsEmpty() {
    SelectDeviceComboBoxAction action = new SelectDeviceComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.emptyList(), action.getDevices());
    assertNull(action.getSelectedDevice());
    assertEquals("No devices", myPresentation.getText());
  }

  @Test
  public void update() {
    Mockito.when(myDevicesGetter.get()).thenReturn(Arrays.asList(
      "Pixel 2 XL API 27",
      "Pixel 2 XL API 28",
      "Pixel API 28",
      "Pixel 2 API 27"));

    SelectDeviceComboBoxAction action = new SelectDeviceComboBoxAction(() -> true, myDevicesGetter);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Arrays.asList("Pixel 2 XL API 27", "Pixel 2 XL API 28", "Pixel API 28", "Pixel 2 API 27"), action.getDevices());
    assertEquals("Pixel 2 XL API 27", action.getSelectedDevice());
    assertEquals("Pixel 2 XL API 27", myPresentation.getText());
  }

  @Test
  public void updateDevicesDoesntContainSelectedDevice() {
    Mockito.when(myDevicesGetter.get()).thenReturn(Arrays.asList("Pixel 2 XL API 27", "Pixel 2 XL API 28", "Pixel API 28"));

    SelectDeviceComboBoxAction action = new SelectDeviceComboBoxAction(() -> true, myDevicesGetter);
    action.setSelectedDevice("Pixel 2 API 27");
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Arrays.asList("Pixel 2 XL API 27", "Pixel 2 XL API 28", "Pixel API 28"), action.getDevices());
    assertEquals("Pixel 2 XL API 27", action.getSelectedDevice());
    assertEquals("Pixel 2 XL API 27", myPresentation.getText());
  }
}
