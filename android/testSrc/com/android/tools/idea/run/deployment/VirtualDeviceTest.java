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
import org.junit.Test;

public final class VirtualDeviceTest {
  @Test
  public void getNameImageCountEqualsZero() {
    assertNull(VirtualDevice.getName(Snapshot.getDefaultInstance(), ""));
  }

  @Test
  public void getNameLogicalNameIsEmpty() {
    Snapshot snapshot = Snapshot.newBuilder()
      .addImages(Image.getDefaultInstance())
      .build();

    String fallbackName = "default_boot";
    assertSame(fallbackName, VirtualDevice.getName(snapshot, fallbackName));
  }

  @Test
  public void getName() {
    Snapshot snapshot = Snapshot.newBuilder()
      .addImages(Image.getDefaultInstance())
      .setLogicalName("My Snapshot")
      .build();

    assertEquals("My Snapshot", VirtualDevice.getName(snapshot, ""));
  }
}
