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
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.util.*;


/**
 * {@link ColorExtractor} using the DBSCAN clustering algorithm.
 *
 * The DBSCAN algorithm allows to gather in the same cluster the myColor that are perceptually almost the same.
 *
 * The clustering is done in the CIELAB myColor space. The CIELAB myColor space is useful since an euclidean distance of 1 between two
 * colors is approximately the minimum perceptual difference between the two myColor (i.e colors with a distance < 1 in CIE LAB will look
 * the same for the human eye).
 *
 * No need to specify the number of color we have to extract
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public class DBSCANColorExtractor extends DoublesColorExtractor {

  /**
   * Maximum Euclidean distance between two myColor in the CIELAB space two gather them in the same cluster
   */
  public static final float DEFAULT_EPS = 1.3f;
  private float myEps;
  private int myMinClusterSize;

  public DBSCANColorExtractor(Mockup mockup) {
    super(mockup);
    myEps = DEFAULT_EPS;
    myMinClusterSize = getMinClusterSize(myImage);
  }

  public DBSCANColorExtractor(BufferedImage image, float eps, int minClusterSize) {
    super(image);
    myEps = eps;
    myMinClusterSize = minClusterSize;
  }

  /**
   * Find the color with the most occurrences in each cluster of similar colors.
   *
   * For each cluster of similar color, parse all the colors and count the number of pixel of this exact color.
   * Then add the color with the most occurrence in an {@link ExtractedColor}
   * and set its number of occurrence in the image at the number of similar color in the cluster.
   *
   * @param LAB_RGB  Map containing the rgb value corresponding to a CIELAB value.
   *                 Should have been populated with {@link DoublesColorExtractor#getLABPixels} before
   * @param clusters the clusters return by {@link DBSCANClusterer#cluster(double[][])}
   * @return a TreeSet of the {@link ExtractedColor} sorted by the number of occurrences for each color (ascending order)
   */
  @NotNull
  private static List<ExtractedColor> getMainColorPerCluster(HashMap<Integer, Integer> LAB_RGB, List<List<double[]>> clusters) {
    List<ExtractedColor> colors = new ArrayList<>(clusters.size());

    for (int clusterIndex = 0; clusterIndex < clusters.size(); clusterIndex++) {

      List<double[]> cluster = clusters.get(clusterIndex);
      HashMap<Integer, Integer> occurrences = new HashMap<>();
      int maxOccurrence = 0;
      int mostPresentColor = 0;

      for (int colorIndex = 0; colorIndex < cluster.size(); colorIndex++) {
        int colorOccurrence;
        double[] LAB = cluster.get(colorIndex);

        // Retrieve the rgb value corresponding to the LAB value
        int rgb = LAB_RGB.get(Arrays.hashCode(LAB));

        if (occurrences.containsKey(rgb)) {
          // If the color is already in the map, increment the number of occurrence
          colorOccurrence = occurrences.get(rgb) + 1;
          occurrences.replace(rgb, colorOccurrence);
        }
        else {
          // Else add the color to the map and set its occurrence to 1
          occurrences.put(rgb, 1);
          colorOccurrence = 1;
        }

        // Update the color occurring the most
        if (colorOccurrence > maxOccurrence) {
          maxOccurrence = colorOccurrence;
          mostPresentColor = rgb;
        }
      }

      // Save the rgb value of all color in cluster in the Extracted color
      Set<Integer> clusterColorSet = new HashSet<>();
      for (int i = 0; i < cluster.size(); i++) {
        clusterColorSet.add(LAB_RGB.get(Arrays.hashCode(cluster.get(i))));
      }
      colors.add(new ExtractedColor(mostPresentColor, cluster.size(), clusterColorSet));
    }
    Collections.sort(colors);
    return colors;
  }

  @Override
  List<ExtractedColor> runClustering(HashMap<Integer, Integer> labToRgb,
                                     List<double[]> clusterInput,
                                     Clusterer.ProgressListener listener) {
    List<ExtractedColor> extractedColors;
    DBSCANClusterer dbscanClusterer = new DBSCANClusterer(myEps, myMinClusterSize, listener);
    final List<List<double[]>> clusters = dbscanClusterer.cluster(
      clusterInput.toArray(new double[clusterInput.size()][POINT_DIMENSION]));
    extractedColors = getMainColorPerCluster(labToRgb, clusters);
    return extractedColors;
  }

  /**
   * Compute the minimum cluster size regarding the imageSize.
   *
   * Minimum cluster size is 0 if image size < 16 else Math.pow(imageSize, 0.25)
   *
   * @param image width or height of the image. Typically the bigger of both.
   * @return the minimum cluster size
   */
  public static int getMinClusterSize(BufferedImage image) {
    int imageSize = Math.max(image.getWidth(), image.getHeight());
    int minClusterSize;
    if (imageSize < 16) {
      minClusterSize = 0;
    }
    else {
      minClusterSize = (int)Math.round(Math.pow(imageSize, 0.25));
    }
    return minClusterSize;
  }
}
