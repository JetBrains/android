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
import static org.junit.Assert.assertSame;

import com.android.emulator.SnapshotOuterClass.Image;
import com.android.emulator.SnapshotOuterClass.Snapshot;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import org.junit.Test;

public final class VirtualDevicesWorkerDelegateTest {
  @Test
  public void getNameSnapshotProtocolBufferDoesntExist() {
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
    Path directory = fileSystem.getPath("/usr/local/google/home/testuser/.android/avd/Pixel_2_XL_API_28.avd/snapshots/default_boot");

    Object actualName = VirtualDevicesWorkerDelegate.getName(directory);

    assertEquals("default_boot", actualName);
  }

  @Test
  public void getNameImageCountEqualsZero() {
    assertNull(VirtualDevicesWorkerDelegate.getName(Snapshot.getDefaultInstance(), ""));
  }

  @Test
  public void getNameLogicalNameIsEmpty() {
    Snapshot snapshot = Snapshot.newBuilder()
      .addImages(Image.getDefaultInstance())
      .build();

    String fallbackName = "default_boot";
    assertSame(fallbackName, VirtualDevicesWorkerDelegate.getName(snapshot, fallbackName));
  }

  @Test
  public void getName() {
    Snapshot snapshot = Snapshot.newBuilder()
      .addImages(Image.getDefaultInstance())
      .setLogicalName("My Snapshot")
      .build();

    assertEquals("My Snapshot", VirtualDevicesWorkerDelegate.getName(snapshot, ""));
  }
}
