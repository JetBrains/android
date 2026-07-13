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

import static com.android.tools.adtui.device.DeviceArtPainter.DeviceData;
import static com.android.tools.adtui.device.DeviceArtPainter.FrameData;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.dvlib.DeviceSchemaTest;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceParser;
import com.android.testutils.GoldenImageRule;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.adtui.webp.WebpNativeLibDownloader;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * If adding a new device (new device art), run the tests to generate crop data and insert
 * this into the device art descriptors for the new devices, then look at generateCropData
 * and remove the early exit to have that method generate new, updated (cropped) images
 * and adjusted device-art descriptors.
 */
public class DeviceArtPainterTest {

  @Rule
  public TemporaryFolder myTemporaryFolder = new TemporaryFolder();

  @Rule
  public GoldenImageRule goldenImageRule = new GoldenImageRule("tools/adt/idea/adt-ui/testData/DeviceArtPaiterTest/golden");

  @Before
  public void setUp() {
    WebpNativeLibDownloader.ensureWebpRegistered();
  }

  @Test
  public void testGenerateCropData() throws Exception {
    // TODO: Assert that the crop data is right
    generateCropData();
  }

  @Test
  public void testRendering() throws Exception {
    File deviceArtPath = DeviceArtDescriptor.getBundledDescriptorsFolder();
    List<DeviceArtDescriptor> descriptors = DeviceArtDescriptor.getDescriptors(new File[]{deviceArtPath});

    for (DeviceArtDescriptor descriptor : descriptors) {
      assertAppearance(descriptor);
    }
  }

  @NotNull
  private static BufferedImage createSampleImage(Dimension size, Color color) {
    @SuppressWarnings("UndesirableClassUsage") // no need to support retina
    BufferedImage img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = img.createGraphics();
    g2d.setColor(color);
    g2d.fillRect(0, 0, size.width, size.height);
    g2d.dispose();
    return img;
  }

  private static void generateCropData() throws Exception {
    DeviceArtPainter framePainter = DeviceArtPainter.getInstance();
    Device device = newDevice();
    for (DeviceArtDescriptor spec : framePainter.getDescriptors()) {
      DeviceData data = new DeviceData(device, spec);
      if (spec.getName().startsWith("Generic ")) {
        // No crop data for generic nine patches since they are stretchable
        continue;
      }
      if (spec.getName().startsWith("Television") || spec.getName().startsWith("Automotive")) {
        // These images are already cropped
        continue;
      }
      Rectangle cropRect = spec.getCrop(ScreenOrientation.LANDSCAPE);
      if (cropRect != null && !cropRect.getSize().equals(spec.getScreenSize(ScreenOrientation.LANDSCAPE))) {
        // Already have crop data for this spec; skipping
        continue;
      }
      System.out.println("for spec " + spec.getName() + " -- " + spec.getId());
      FrameData landscapeData = data.getFrameData(ScreenOrientation.LANDSCAPE, Integer.MAX_VALUE);
      // Must use computeImage rather than getImage here since we want to get the
      // full size images, not the already cropped images

      BufferedImage effectsImage;
      Rectangle crop;
      ImageUtils.CropFilter filter = (bufferedImage, x, y) -> {
        int rgb = bufferedImage.getRGB(x, y);
        return ((rgb & 0xFF000000) >>> 24) < 2;
      };

      FrameData portraitData = data.getFrameData(ScreenOrientation.PORTRAIT, Integer.MAX_VALUE);
      try {
        effectsImage = portraitData.computeImage(true, 0, 0, portraitData.getFrameWidth(), portraitData.getFrameHeight());
      } catch (OutOfMemoryError oome) {
        // This test sometimes fails on the build server because it runs out of memory; it's a memory
        // hungry test which sometimes fails when run as part of thousands of other tests.
        // Ignore those types of failures.
        // Make sure it's not failing to allocate memory due to some crazy large bounds we didn't anticipate:
        assertTrue(portraitData.getFrameWidth() < 4000);
        assertTrue(portraitData.getFrameHeight() < 4000);
        return;
      }

      assertNotNull(effectsImage);
      crop = ImageUtils.getCropBounds(effectsImage, filter, null);
      assertNotNull(crop);
      System.out.print("      port crop=\"");
      System.out.print(crop.x);
      System.out.print(",");
      System.out.print(crop.y);
      System.out.print(",");
      System.out.print(crop.width);
      System.out.print(",");
      System.out.print(crop.height);
      System.out.println("\"");

      try {
        effectsImage = landscapeData.computeImage(true, 0, 0, landscapeData.getFrameWidth(), landscapeData.getFrameHeight());
      } catch (OutOfMemoryError oome) {
        // See portrait case above
        assertTrue(landscapeData.getFrameWidth() < 4000);
        assertTrue(landscapeData.getFrameHeight() < 4000);
        return;
      }
      assertNotNull(effectsImage);
      crop = ImageUtils.getCropBounds(effectsImage, filter, null);
      assertNotNull(crop);
      System.out.print("      landscape crop=\"");
      System.out.print(crop.x);
      System.out.print(",");
      System.out.print(crop.y);
      System.out.print(",");
      System.out.print(crop.width);
      System.out.print(",");
      System.out.print(crop.height);
      System.out.println("\"");
    }
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private static Device newDevice() throws Exception {
    Collection<Device> devices;
    InputStream stream = null;
    try {
      stream = DeviceSchemaTest.class.getResourceAsStream("devices_minimal.xml");
      devices = DeviceParser.parse(stream).values();
    } finally {
      if (stream != null) {
        stream.close();
      }
    }
    assertTrue(!devices.isEmpty());
    return devices.iterator().next();
  }

  private void assertAppearance(DeviceArtDescriptor descriptor) throws IOException {
    BufferedImage sample = createSampleImage(new Dimension(400, 800), Color.RED);
    BufferedImage image = DeviceArtPainter.createFrame(sample, descriptor);
    goldenImageRule.assertImageSimilar(descriptor.getId(), image, 0.0);
  }
}
