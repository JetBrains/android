/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.mockup.colorextractor;

import com.android.tools.pixelprobe.color.Colors;
import junit.framework.TestCase;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;

public class DoubleColorExtractorTest extends TestCase {

  public void testGetLABPixels() throws Exception {

    BufferedImage bufferedImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    int[] pixels = new int[100];
    for (int i = 0; i < pixels.length; i++) {
      pixels[i] = 0xDDDDDD;
    }
    bufferedImage.setRGB(0,0,10,10,pixels,0,10);

    HashMap<Integer, Integer> labToRgb = new HashMap<>();
    HashMap<Integer, double[]> rgbToLab = new HashMap<>();
    List<double[]> labPixels = DoublesColorExtractor.getLABPixels(bufferedImage, labToRgb, rgbToLab, null);
    float[] floats = Colors.getLabColorSpace().fromRGB(new float[]{0xDD / 255f, 0xDD / 255f, 0xDD / 255f});
    double[] pixel = labPixels.get(0);
    assertEquals(floats[0], pixel[0], 1);
    assertEquals(floats[1], pixel[1], 1);
    assertEquals(floats[2], pixel[2], 1);
  }
}