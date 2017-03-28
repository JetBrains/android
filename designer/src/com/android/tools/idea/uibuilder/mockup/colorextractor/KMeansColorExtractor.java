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

import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.pixelprobe.color.Colors;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * {@link ColorExtractor} using the Kmeans algorithm to extract k color from the image.
 *
 * K default to {@value #K}
 */
public class KMeansColorExtractor extends DoublesColorExtractor {

  private static int K = 10;
  private int myK = K;

  /**
   * Create a new color extractor based on the Kmeans algorithm with a k = {@value #K}
   * @param mockup the mockup to extract the color from
   */
  @SuppressWarnings("unused")
  KMeansColorExtractor(Mockup mockup) {
    super(mockup);
  }

  KMeansColorExtractor(BufferedImage image, int k) {
    super(image);
    myK = k;
  }

  @Override
  List<ExtractedColor> runClustering(HashMap<Integer, Integer> labToRgb,
                                     List<double[]> clusterInput,
                                     Clusterer.ProgressListener listener) {
    List<ExtractedColor> extractedColors;
    List<List<double[]>> clusters = new KMeansClusterer(myK).cluster(clusterInput.toArray(new double[clusterInput.size()][POINT_DIMENSION]));
    extractedColors = new ArrayList<>(clusters.size());

    ColorSpace labColorSpace = Colors.getLabColorSpace();
    for (List<double[]> cluster : clusters) {
      double[] color = cluster.get(0);

      float[] rgb = labColorSpace.toRGB(new float[]{(float)color[0], (float)color[1], (float)color[2]});

      int rgbInt = Math.round(rgb[0] * 255) << 16 | Math.round(rgb[1] * 255) << 8 | Math.round(rgb[2] * 255);
      extractedColors.add(new ExtractedColor(rgbInt, 1, null));
    }
    return extractedColors;
  }
}
