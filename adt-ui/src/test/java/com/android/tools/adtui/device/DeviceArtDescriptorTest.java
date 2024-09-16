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
package com.android.tools.adtui.device;

import com.android.resources.ScreenOrientation;
import com.android.tools.adtui.webp.WebpMetadata;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("ConstantConditions")
public class DeviceArtDescriptorTest extends TestCase {

  @Override
  public void setUp() {
    WebpMetadata.ensureWebpRegistered();
  }

  public void testBasics() throws IOException {
    List<DeviceArtDescriptor> specs = DeviceArtDescriptor.getDescriptors(null);

    assertEquals(2, specs.size());

    for (DeviceArtDescriptor descriptor : specs) {
      String id = descriptor.getId();
      assertNotNull(id);
      assertNotNull(descriptor.getName());
      for (ScreenOrientation orientation : new ScreenOrientation[] { ScreenOrientation.LANDSCAPE, ScreenOrientation.PORTRAIT}) {
        if (orientation == ScreenOrientation.PORTRAIT) {
          continue;
        }
        assertNotNull(id, descriptor.getFrameSize(orientation));
        assertNotNull(id, descriptor.getScreenPos(orientation));
        assertNotNull(id, descriptor.getScreenSize(orientation));

        // We've pre-subtracted the crop everywhere now
        assertNull(descriptor.getCrop(orientation));
        File frame = descriptor.getFrame(orientation);
        assertTrue(frame.getParentFile().getName() + '/' + frame.getName() + " does not exist", frame.exists());
        File dropShadow = descriptor.getDropShadow(orientation);
        if (dropShadow != null) {
          assertTrue(dropShadow.getParentFile().getName() + '/' + dropShadow.getName() + " does not exist", dropShadow.exists());
        }
        File reflectionOverlay = descriptor.getReflectionOverlay(orientation);
        if (reflectionOverlay != null) {
          assertTrue(reflectionOverlay.getParentFile().getName() + '/' + reflectionOverlay.getName() + " does not exist",
                     reflectionOverlay.exists());
        }

        verifyCompatibleImage(frame);
        verifyCompatibleImage(dropShadow);
        verifyCompatibleImage(reflectionOverlay);
        verifyCompatibleImage(descriptor.getMask(orientation));
      }
    }

    DeviceArtDescriptor generic_phone = getDescriptorFor("phone", specs);
    assertNull(generic_phone.getReflectionOverlay(ScreenOrientation.LANDSCAPE));
  }

  public void testCanFrameImage() {
    // Regression test for issue 72580
    //noinspection UndesirableClassUsage
    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
    List<DeviceArtDescriptor> specs = DeviceArtDescriptor.getDescriptors(null);
    for (DeviceArtDescriptor spec : specs) {
      spec.canFrameImage(image, ScreenOrientation.LANDSCAPE);
      spec.canFrameImage(image, ScreenOrientation.PORTRAIT);
    }
  }

  private static void verifyFileExists(@Nullable File f) {
    assertNotNull(f);
    assertTrue(f.exists());
  }

  private static void verifyCompatibleImage(@Nullable File file) throws IOException {
    if (file == null) {
      return;
    }

    BufferedImage image = ImageIO.read(file);

    // ImageIO does not handle all possible image formats; let's not use any that we don't recognize!
    assertTrue("Unrecognized type " + file, image.getType() != BufferedImage.TYPE_CUSTOM);
  }

  private static DeviceArtDescriptor getDescriptorFor(@NotNull String id, List<DeviceArtDescriptor> descriptors) {
    for (DeviceArtDescriptor descriptor : descriptors) {
      if (id.equals(descriptor.getId())) {
        return descriptor;
      }
    }
    return null;
  }
}
