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

import com.android.dvlib.DeviceSchemaTest;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceParser;
import com.android.tools.idea.rendering.ImageUtils;
import junit.framework.TestCase;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static com.android.tools.idea.ddms.screenshot.DeviceArtPainter.DeviceData;
import static com.android.tools.idea.ddms.screenshot.DeviceArtPainter.FrameData;

public class DeviceArtPainterTest extends TestCase {
  public void testGenerateCropData() throws Exception {
    // TODO: Assert that the crop data is right
    generateCropData();
  }

  public void generateCropData() throws Exception {
    DeviceArtPainter framePainter = DeviceArtPainter.getInstance();
    Device device = newDevice();
    for (DeviceArtDescriptor spec : framePainter.getDescriptors()) {
      DeviceData data = new DeviceData(device, spec);
      System.out.println("for spec " + spec.getName() + " -- " + spec.getId());
      FrameData landscapeData = data.getFrameData(ScreenOrientation.LANDSCAPE, Integer.MAX_VALUE);
      // Must use computeImage rather than getImage here since we want to get the
      // full size images, not the already cropped images
      BufferedImage effectsImage = landscapeData.computeImage(true, 0, 0, landscapeData.getFrameWidth(), landscapeData.getFrameHeight());
      assertNotNull(effectsImage);
      ImageUtils.CropFilter filter = new ImageUtils.CropFilter() {
        @Override
        public boolean crop(BufferedImage bufferedImage, int x, int y) {
          int rgb = bufferedImage.getRGB(x, y);
          return ((rgb & 0xFF000000) >>> 24) < 2;
        }
      };
      Rectangle crop = ImageUtils.getCropBounds(effectsImage, filter, null);
      assertNotNull(crop);
      System.out.print("      landCrop: [");
      System.out.print(crop.x);
      System.out.print(",");
      System.out.print(crop.y);
      System.out.print(",");
      System.out.print(crop.x + crop.width);
      System.out.print(",");
      System.out.print(crop.y+crop.height);
      System.out.println("],");

      FrameData portraitData = data.getFrameData(ScreenOrientation.PORTRAIT, Integer.MAX_VALUE);
      effectsImage = portraitData.computeImage(true, 0, 0, portraitData.getFrameWidth(), portraitData.getFrameHeight());
      assertNotNull(effectsImage);
      crop = ImageUtils.getCropBounds(effectsImage, filter, null);
      assertNotNull(crop);
      System.out.print("      portCrop: [");
      System.out.print(crop.x);
      System.out.print(",");
      System.out.print(crop.y);
      System.out.print(",");
      System.out.print(crop.x+crop.width);
      System.out.print(",");
      System.out.print(crop.y+crop.height);
      System.out.println("],");
    }
  }

  private static Device newDevice() throws Exception {
    java.util.List<Device> devices;
    InputStream stream = null;
    try {
      stream = DeviceSchemaTest.class.getResourceAsStream("devices_minimal.xml");
      devices = DeviceParser.parse(stream);
    } finally {
      if (stream != null) {
        stream.close();
      }
    }
    assertTrue(!devices.isEmpty());
    return devices.get(0);
  }
}
