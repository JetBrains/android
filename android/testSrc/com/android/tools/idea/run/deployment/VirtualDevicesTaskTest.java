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
import static org.junit.Assert.assertNull;

import com.android.emulator.SnapshotOuterClass;
import com.android.emulator.SnapshotOuterClass.Image;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class VirtualDevicesTaskTest {
  private FileSystem myFileSystem;

  private VirtualDevicesTask myTask;

  @Before
  public void setUp() {
    myFileSystem = Jimfs.newFileSystem(Configuration.unix());
    myTask = new VirtualDevicesTask(true, myFileSystem, null);
  }

  @Test
  public void getSnapshotSnapshotProtocolBufferDoesntExist() {
    // Arrange
    Path directory = myFileSystem.getPath("/usr/local/google/home/testuser/.android/avd/Pixel_2_XL_API_28.avd/snapshots/default_boot");

    // Act
    Object snapshot = myTask.getSnapshot(directory);

    // Assert
    assertEquals(Snapshot.quickboot(myFileSystem), snapshot);
  }

  @Test
  public void getSnapshotImageCountEqualsZero() {
    // Arrange
    SnapshotOuterClass.Snapshot protocolBufferSnapshot = SnapshotOuterClass.Snapshot.getDefaultInstance();
    Path directory = myFileSystem.getPath("");

    // Act
    Object snapshot = myTask.getSnapshot(protocolBufferSnapshot, directory);

    // Assert
    assertNull(snapshot);
  }

  @Test
  public void getSnapshotLogicalNameIsEmpty() {
    // Arrange
    SnapshotOuterClass.Snapshot protocolBufferSnapshot = SnapshotOuterClass.Snapshot.newBuilder()
      .addImages(Image.getDefaultInstance())
      .build();

    Path directory = Snapshot.defaultBoot(myFileSystem);

    // Act
    Object snapshot = myTask.getSnapshot(protocolBufferSnapshot, directory);

    // Assert
    assertEquals(Snapshot.quickboot(myFileSystem), snapshot);
  }

  @Test
  public void getSnapshot() {
    // Arrange
    SnapshotOuterClass.Snapshot protocolBufferSnapshot = SnapshotOuterClass.Snapshot.newBuilder()
      .addImages(Image.getDefaultInstance())
      .setLogicalName("My Snapshot")
      .build();

    Path directory = myFileSystem.getPath("");

    // Act
    Object snapshot = myTask.getSnapshot(protocolBufferSnapshot, directory);

    // Assert
    assertEquals(new Snapshot(directory, "My Snapshot"), snapshot);
  }
}
