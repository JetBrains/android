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
package com.android.tools.idea.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.AndroidDebugBridge;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import icons.StudioIcons;
import java.io.File;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class AdbConnectionStatusActionTest {
  private Presentation myPresentation;
  private AnActionEvent myEvent;

  private Future<AndroidDebugBridge> myFuture;

  @Before
  public void mockEvent() {
    myPresentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);
    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
  }

  @Before
  public void mockFuture() {
    // noinspection unchecked
    myFuture = Mockito.mock(Future.class);
  }

  @Test
  public void updateAdbConnectionStatusActionIsntVisible() {
    // Arrange
    AnAction action = new AdbConnectionStatusAction(() -> false, project -> null, adb -> null);

    // Act
    action.update(myEvent);

    // Assert
    assertFalse(myPresentation.isVisible());
  }

  @Test
  public void updateAdbIsNull() {
    // Arrange
    AnAction action = new AdbConnectionStatusAction(() -> true, project -> null, adb -> null);

    // Act
    action.update(myEvent);

    // Assert
    assertTrue(myPresentation.isVisible());
    assertEquals(StudioIcons.Common.ERROR, myPresentation.getIcon());
    assertEquals("adb executable not found", myPresentation.getDescription());
  }

  @Test
  public void updateFutureIsntDone() {
    // Arrange
    AnAction action = new AdbConnectionStatusAction(
      () -> true,
      project -> new File("/home/juancnuno/Android/Sdk/platform-tools/adb"),
      adb -> myFuture);

    // Act
    action.update(myEvent);

    // Assert
    assertTrue(myPresentation.isVisible());
    assertEquals(StudioIcons.Common.ERROR, myPresentation.getIcon());
    assertEquals("Connecting to adb...", myPresentation.getDescription());
  }

  @Test
  public void updateFutureIsCancelled() {
    // Arrange
    Mockito.when(myFuture.isDone()).thenReturn(true);
    Mockito.when(myFuture.isCancelled()).thenReturn(true);

    AnAction action = new AdbConnectionStatusAction(
      () -> true,
      project -> new File("/home/juancnuno/Android/Sdk/platform-tools/adb"),
      adb -> myFuture);

    // Act
    action.update(myEvent);

    // Assert
    assertTrue(myPresentation.isVisible());
    assertEquals(StudioIcons.Common.ERROR, myPresentation.getIcon());
    assertEquals("Connecting to adb...", myPresentation.getDescription());
  }

  @Test
  public void update() {
    // Arrange
    Mockito.when(myFuture.isDone()).thenReturn(true);

    AnAction action = new AdbConnectionStatusAction(
      () -> true,
      project -> new File("/home/juancnuno/Android/Sdk/platform-tools/adb"),
      adb -> myFuture);

    // Act
    action.update(myEvent);

    // Assert
    assertTrue(myPresentation.isVisible());
    assertEquals(StudioIcons.Common.SUCCESS, myPresentation.getIcon());
    assertEquals("Connected to adb", myPresentation.getDescription());
  }
}
