/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SelectMultipleDevicesActionTest {
  private final @NotNull AsyncDevicesGetter myGetter;
  private final @NotNull AnAction myAction;

  private final @NotNull Presentation myPresentation;

  private final @NotNull AnActionEvent myEvent;

  public SelectMultipleDevicesActionTest() {
    myGetter = Mockito.mock(AsyncDevicesGetter.class);
    myAction = new SelectMultipleDevicesAction(project -> myGetter);

    myPresentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);
    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
  }

  @Test
  public void updateProjectIsNull() {
    // Act
    myAction.update(myEvent);

    // Assert
    assertFalse(myPresentation.isEnabled());
    assertFalse(myPresentation.isVisible());
  }

  @Test
  public void updateGetReturnsOptionalEmpty() {
    // Arrange
    Mockito.when(myEvent.getProject()).thenReturn(Mockito.mock(Project.class));

    // Act
    myAction.update(myEvent);

    // Assert
    assertFalse(myPresentation.isEnabled());
    assertFalse(myPresentation.isVisible());
  }

  @Test
  public void update() {
    // Arrange
    Mockito.when(myGetter.get()).thenReturn(Optional.of(Collections.singletonList(TestDevices.buildPixel4Api30())));

    Mockito.when(myEvent.getProject()).thenReturn(Mockito.mock(Project.class));

    // Act
    myAction.update(myEvent);

    // Assert
    assertTrue(myPresentation.isEnabled());
    assertTrue(myPresentation.isVisible());
  }
}
