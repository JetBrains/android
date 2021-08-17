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

import com.android.emulator.snapshot.SnapshotOuterClass;
import com.android.emulator.snapshot.SnapshotOuterClass.Image;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.run.AndroidDevice;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDevicesTaskTest {
  private final FileSystem myFileSystem = Jimfs.newFileSystem(Configuration.unix());
  private final AndroidDevice myAndroidDevice = Mockito.mock(AndroidDevice.class);

  @Test
  public void getSnapshotIsntNull() throws Exception {
    // Arrange
    Collection<AvdInfo> avds = Collections.singletonList(mockAvd("Pixel 4 API 30",
                                                                 "/home/juancnuno/.android/avd/Pixel_4_API_30.avd",
                                                                 "Pixel_4_API_30"));

    Files.createDirectories(myFileSystem.getPath("/home/juancnuno/.android/avd/Pixel_4_API_30.avd/snapshots/default_boot"));

    AsyncSupplier<Collection<VirtualDevice>> task = new VirtualDevicesTask.Builder()
      .setExecutorService(MoreExecutors.newDirectExecutorService())
      .setGetAvds(() -> avds)
      .setFileSystem(myFileSystem)
      .setNewLaunchableAndroidDevice(avd -> myAndroidDevice)
      .build();

    // Act
    Future<Collection<VirtualDevice>> future = task.get();

    // Assert
    Object device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDevicePath("/home/juancnuno/.android/avd/Pixel_4_API_30.avd"))
      .setAndroidDevice(myAndroidDevice)
      .setNameKey(new VirtualDeviceName("Pixel_4_API_30"))
      .build();

    assertEquals(Collections.singletonList(device), future.get());
  }

  private static @NotNull AvdInfo mockAvd(@NotNull @SuppressWarnings("SameParameterValue") String displayName,
                                          @NotNull @SuppressWarnings("SameParameterValue") String path,
                                          @NotNull @SuppressWarnings("SameParameterValue") String name) {
    AvdInfo avd = Mockito.mock(AvdInfo.class);

    Mockito.when(avd.getDisplayName()).thenReturn(displayName);
    Mockito.when(avd.getDataFolderPath()).thenReturn(path);
    Mockito.when(avd.getName()).thenReturn(name);

    return avd;
  }

  @Test
  public void getSnapshotSnapshotProtocolBufferDoesntExist() {
    // Arrange
    VirtualDevicesTask task = new VirtualDevicesTask.Builder()
      .setExecutorService(MoreExecutors.newDirectExecutorService())
      .setGetAvds(Collections::emptyList)
      .setFileSystem(myFileSystem)
      .setNewLaunchableAndroidDevice(avd -> myAndroidDevice)
      .build();

    Path directory = myFileSystem.getPath("/usr/local/google/home/testuser/.android/avd/Pixel_2_XL_API_28.avd/snapshots/default_boot");

    // Act
    Object snapshot = task.getSnapshot(directory);

    // Assert
    assertEquals(new Snapshot(directory), snapshot);
  }

  @Test
  public void getSnapshotImageCountEqualsZero() {
    // Arrange
    VirtualDevicesTask task = new VirtualDevicesTask.Builder()
      .setExecutorService(MoreExecutors.newDirectExecutorService())
      .setGetAvds(Collections::emptyList)
      .setFileSystem(myFileSystem)
      .setNewLaunchableAndroidDevice(avd -> myAndroidDevice)
      .build();

    SnapshotOuterClass.Snapshot protocolBufferSnapshot = SnapshotOuterClass.Snapshot.getDefaultInstance();
    Path directory = myFileSystem.getPath("");

    // Act
    Object snapshot = task.getSnapshot(protocolBufferSnapshot, directory);

    // Assert
    assertNull(snapshot);
  }

  @Test
  public void getSnapshotLogicalNameIsEmpty() {
    // Arrange
    VirtualDevicesTask task = new VirtualDevicesTask.Builder()
      .setExecutorService(MoreExecutors.newDirectExecutorService())
      .setGetAvds(Collections::emptyList)
      .setFileSystem(myFileSystem)
      .setNewLaunchableAndroidDevice(avd -> myAndroidDevice)
      .build();

    SnapshotOuterClass.Snapshot protocolBufferSnapshot = SnapshotOuterClass.Snapshot.newBuilder()
      .addImages(Image.getDefaultInstance())
      .build();

    Path directory = myFileSystem.getPath("/usr/local/google/home/testuser/.android/avd/Pixel_2_XL_API_28.avd/snapshots/default_boot");

    // Act
    Object snapshot = task.getSnapshot(protocolBufferSnapshot, directory);

    // Assert
    assertEquals(new Snapshot(directory), snapshot);
  }

  @Test
  public void getSnapshot() {
    // Arrange
    VirtualDevicesTask task = new VirtualDevicesTask.Builder()
      .setExecutorService(MoreExecutors.newDirectExecutorService())
      .setGetAvds(Collections::emptyList)
      .setFileSystem(myFileSystem)
      .setNewLaunchableAndroidDevice(avd -> myAndroidDevice)
      .build();

    SnapshotOuterClass.Snapshot protocolBufferSnapshot = SnapshotOuterClass.Snapshot.newBuilder()
      .addImages(Image.getDefaultInstance())
      .setLogicalName("My Snapshot")
      .build();

    Path directory = myFileSystem.getPath("");

    // Act
    Object snapshot = task.getSnapshot(protocolBufferSnapshot, directory);

    // Assert
    assertEquals(new Snapshot(directory, "My Snapshot"), snapshot);
  }
}
