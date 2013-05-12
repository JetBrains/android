/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ddms.screenshot;

import com.android.resources.ScreenOrientation;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

@SuppressWarnings("ConstantConditions")
public class DeviceArtDescriptorTest extends TestCase {
  public void test1() throws FileNotFoundException {
    List<DeviceArtDescriptor> specs = DeviceArtDescriptor.getDescriptors(null);

    // Currently there are 7 devices for which we have device art, plus 2 generic/stretchable
    assertEquals(9, specs.size());

    DeviceArtDescriptor nexus4 = specs.get(0);
    assertEquals("nexus_4", nexus4.getId());

    Point offsets = nexus4.getScreenPos(ScreenOrientation.PORTRAIT);
    assertEquals(213, offsets.x);
    assertEquals(350, offsets.y);

    offsets = nexus4.getScreenPos(ScreenOrientation.LANDSCAPE);
    assertEquals(349, offsets.x);
    assertEquals(214, offsets.y);

    verifyFileExists(nexus4.getFrame(ScreenOrientation.LANDSCAPE));
    verifyFileExists(nexus4.getFrame(ScreenOrientation.PORTRAIT));
    verifyFileExists(nexus4.getDropShadow(ScreenOrientation.LANDSCAPE));
    verifyFileExists(nexus4.getDropShadow(ScreenOrientation.PORTRAIT));
    verifyFileExists(nexus4.getReflectionOverlay(ScreenOrientation.LANDSCAPE));
    verifyFileExists(nexus4.getReflectionOverlay(ScreenOrientation.PORTRAIT));

    for (DeviceArtDescriptor descriptor : specs) {
      String id = descriptor.getId();
      assertNotNull(id);
      assertNotNull(descriptor.getName());
      for (ScreenOrientation orientation : new ScreenOrientation[] { ScreenOrientation.LANDSCAPE, ScreenOrientation.PORTRAIT}) {
        assertNotNull(id, descriptor.getFrameSize(orientation));
        assertNotNull(id, descriptor.getScreenPos(orientation));
        assertNotNull(id, descriptor.getScreenSize(orientation));
        if (id.equals("phone") || id.equals("tablet")) {
          // No crop for these
        } else {
          assertNotNull(id, descriptor.getCrop(orientation));
        }
        assertTrue(id, descriptor.getFrame(orientation).exists());
        assertTrue(id, descriptor.getDropShadow(orientation).exists());
        File reflectionOverlay = descriptor.getReflectionOverlay(orientation);
        if (reflectionOverlay != null) {
          assertTrue(id, reflectionOverlay.exists());
        }
      }
    }
  }

  private static void verifyFileExists(@Nullable File f) {
    assertNotNull(f);
    assertTrue(f.exists());
  }
}
