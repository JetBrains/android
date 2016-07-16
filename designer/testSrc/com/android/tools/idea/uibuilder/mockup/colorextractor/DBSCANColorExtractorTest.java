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

import com.android.tools.idea.uibuilder.LayoutTestCase;
import junit.framework.TestCase;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DBSCANColorExtractorTest extends TestCase {
  public void testRunClustering() throws Exception {
    BufferedImage image = ImageIO.read(new File(LayoutTestCase.getTestDataPath(), "mockup/4.png"));
    HashMap<Integer, Integer> labToRgb = new HashMap<>();
    List<double[]> labPixels = DoublesColorExtractor.getLABPixels(image, labToRgb, new HashMap<>(), null);
    DBSCANColorExtractor dbscanColorExtractor = new DBSCANColorExtractor(image, 1.3f, 1);
    List<ExtractedColor> extractedColors = dbscanColorExtractor.runClustering(labToRgb, labPixels, null);

    assertEquals(4, extractedColors.size());
    assertTrue(extractedColors.contains(new ExtractedColor(0xFF0000FF, 10, null)));
    assertTrue(extractedColors.contains(new ExtractedColor(0xFF00FF00, 10, null)));
    assertTrue(extractedColors.contains(new ExtractedColor(0xFFFFFFFF, 10, null)));
    assertTrue(extractedColors.contains(new ExtractedColor(0xFFFF0000, 10, null)));
  }

  public void testGetMinClusterSize_over16() throws Exception {
    BufferedImage image = new BufferedImage(256, 200, BufferedImage.TYPE_INT_RGB);
    int minClusterSize = DBSCANColorExtractor.getMinClusterSize(image);
    assertEquals(4, minClusterSize);
  }

  public void testGetMinClusterSize_below16() throws Exception {
    BufferedImage image = new BufferedImage(15, 10, BufferedImage.TYPE_INT_RGB);
    int minClusterSize = DBSCANColorExtractor.getMinClusterSize(image);
    assertEquals(0, minClusterSize);
  }
}