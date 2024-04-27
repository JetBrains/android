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
package com.android.tools.idea.run.deployment.legacyselector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.emulator.snapshot.SnapshotOuterClass;
import com.android.emulator.snapshot.SnapshotOuterClass.Image;
import com.android.prefs.AndroidLocationsException;
import com.android.prefs.AndroidLocationsProvider;
import com.android.prefs.FakeAndroidLocationsProvider;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.testutils.file.InMemoryFileSystems;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.deployment.legacyselector.Device.Type;
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
  private final FileSystem myFileSystem = InMemoryFileSystems.createInMemoryFileSystem();

  private final AndroidLocationsProvider myProvider = new FakeAndroidLocationsProvider(myFileSystem);
  private final AndroidDevice myAndroidDevice = Mockito.mock(AndroidDevice.class);

  @Test
  public void getSnapshotIsntNull() throws Exception {
    // Arrange
    Path path = myProvider.getAvdLocation().resolve("Pixel_4_API_30.avd");
    Collection<AvdInfo> avds = Collections.singletonList(mockAvd("Pixel 4 API 30", path, "Pixel_4_API_30"));

    Files.createDirectories(path.resolve(myFileSystem.getPath("snapshots", "default_boot")));

    AsyncSupplier<Collection<VirtualDevice>> task = new VirtualDevicesTask.Builder()
      .setExecutorService(MoreExecutors.newDirectExecutorService())
      .setGetAvds(() -> avds)
      .setNewLaunchableAndroidDevice(avd -> myAndroidDevice)
      .setCheckerSupplier(() -> null)
      .build();

    // Act
    Future<Collection<VirtualDevice>> future = task.get();

    // Assert
    Object device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setType(Type.PHONE)
      .setKey(new VirtualDevicePath(path))
      .setAndroidDevice(myAndroidDevice)
      .setNameKey(new VirtualDeviceName("Pixel_4_API_30"))
      .build();

    assertEquals(Collections.singletonList(device), future.get());
  }

  private @NotNull AvdInfo mockAvd(@NotNull @SuppressWarnings("SameParameterValue") String displayName,
                                   @NotNull @SuppressWarnings("SameParameterValue") Path path,
                                   @NotNull @SuppressWarnings("SameParameterValue") String name) {
    AvdInfo avd = Mockito.mock(AvdInfo.class);

    Mockito.when(avd.getDisplayName()).thenReturn(displayName);
    Mockito.when(avd.getDataFolderPath()).thenReturn(path);
    Mockito.when(avd.getName()).thenReturn(name);

    return avd;
  }

  @Test
  @SuppressWarnings("RedundantThrows")
  public void getSnapshotSnapshotProtocolBufferDoesntExist() throws AndroidLocationsException {
    // Arrange
    VirtualDevicesTask task = new VirtualDevicesTask.Builder()
      .setExecutorService(MoreExecutors.newDirectExecutorService())
      .setGetAvds(Collections::emptyList)
      .setNewLaunchableAndroidDevice(avd -> myAndroidDevice)
      .setCheckerSupplier(() -> null)
      .build();

    Path directory = myProvider.getAvdLocation().resolve(myFileSystem.getPath("Pixel_2_XL_API_28.avd", "snapshots", "default_boot"));

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
      .setNewLaunchableAndroidDevice(avd -> myAndroidDevice)
      .setCheckerSupplier(() -> null)
      .build();

    SnapshotOuterClass.Snapshot protocolBufferSnapshot = SnapshotOuterClass.Snapshot.getDefaultInstance();
    Path directory = myFileSystem.getPath("");

    // Act
    Object snapshot = task.getSnapshot(protocolBufferSnapshot, directory);

    // Assert
    assertNull(snapshot);
  }

  @Test
  @SuppressWarnings("RedundantThrows")
  public void getSnapshotLogicalNameIsEmpty() throws AndroidLocationsException {
    // Arrange
    VirtualDevicesTask task = new VirtualDevicesTask.Builder()
      .setExecutorService(MoreExecutors.newDirectExecutorService())
      .setGetAvds(Collections::emptyList)
      .setNewLaunchableAndroidDevice(avd -> myAndroidDevice)
      .setCheckerSupplier(() -> null)
      .build();

    SnapshotOuterClass.Snapshot protocolBufferSnapshot = SnapshotOuterClass.Snapshot.newBuilder()
      .addImages(Image.getDefaultInstance())
      .build();

    Path directory = myProvider.getAvdLocation().resolve(myFileSystem.getPath("Pixel_2_XL_API_28.avd", "snapshots", "default_boot"));

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
      .setNewLaunchableAndroidDevice(avd -> myAndroidDevice)
      .setCheckerSupplier(() -> null)
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
