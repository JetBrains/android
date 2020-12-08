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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import java.nio.file.FileSystem;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SelectSnapshotActionTest {
  @Test
  public void update() {
    // Arrange
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
    Snapshot snapshot = new Snapshot(fileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-07_16-36-58"));
    AnAction action = new SelectSnapshotAction(snapshot);

    Presentation presentation = new Presentation();

    AnActionEvent event = Mockito.mock(AnActionEvent.class);
    Mockito.when(event.getPresentation()).thenReturn(presentation);

    // Act
    action.update(event);

    // Assert
    assertEquals("snap_2020-12-07_16-36-58", presentation.getText());
  }
}
