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
package com.android.tools.idea.devicemanager.physicaltab;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StorageDeviceTest {
  private static final Object STORAGE_DEVICE = new StorageDevice(107_599);

  @Test
  public void newStorageDevice() {
    // Arrange
    List<String> output = Arrays.asList("Filesystem      1K-blocks    Used Available Use% Mounted on",
                                        "/dev/block/dm-8 116570092 6256928 110182092   6% /data/user/0",
                                        "");

    // Act
    Object device = StorageDevice.newStorageDevice(output);

    // Assert
    assertEquals(Optional.of(STORAGE_DEVICE), device);
  }

  @Test
  public void testToString() {
    // Act
    Object string = STORAGE_DEVICE.toString();

    // Assert
    assertEquals("107,599 MB", string);
  }
}
