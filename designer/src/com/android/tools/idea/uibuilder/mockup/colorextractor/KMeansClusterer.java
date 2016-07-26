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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Use tge KMeans to clusterize a set of points into k clusters
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public final class KMeansClusterer implements Clusterer {

  public static final int PASS_NUMBER = 100;
  private final int myK;
  private int myInputDataSize;
  private double[][] myData;
  private double[][] myOutputColors;

  public KMeansClusterer(int k) {
    myK = k;
    myOutputColors = new double[myK][3];
  }

  @Override
  public List<List<double[]>> cluster(double[][] points) {
    myData = points;
    myInputDataSize = points.length;
    getInitialColors();
    for (int i = 0; i < PASS_NUMBER; i++) {
      int dist = pass();
      if (dist == 0) break;
    }
    List<List<double[]>> clusters = new ArrayList<>(myOutputColors.length);
    for (int i = 0; i < myOutputColors.length; i++) {
      ArrayList<double[]> cluster = new ArrayList<>(1);
      cluster.add(myOutputColors[i]);
      clusters.add(cluster);
    }
    return clusters;
  }

  int pass() {
    // Reset center and count
    double[][] centers = new double[myK][3];
    int[] count = new int[myK];

    for (int pointIndex = 0; pointIndex < myInputDataSize; pointIndex++) {
      double minDist = Double.MAX_VALUE;
      int closestColor = 0;

      // Foreach point in the input data, find the output color which is the closest
      for (int outputIndex = 0; outputIndex < myOutputColors.length; outputIndex++) {
        double distance = distance(myOutputColors[outputIndex], myData[pointIndex]);
        if (distance < minDist) {
          minDist = distance;
          closestColor = outputIndex;
        }
      }

      // Add the value to compute the new center (find the average)
      centers[closestColor][0] += myData[pointIndex][0];
      centers[closestColor][1] += myData[pointIndex][1];
      centers[closestColor][2] += myData[pointIndex][2];
      count[closestColor]++;
    }

    int dist = 0;

    // Update each output color to the new center
    for (int j = 0; j < myOutputColors.length; j++) {
      if (count[j] == 0) continue;
      int l = (int)centers[j][0] / count[j];
      int a = (int)centers[j][1] / count[j];
      int b = (int)centers[j][2] / count[j];
      double[] oldColor = new double[3];
      System.arraycopy(myOutputColors[j], 0, oldColor, 0, 3);

      myOutputColors[j][0] = l;
      myOutputColors[j][1] = a;
      myOutputColors[j][2] = b;
      dist += distance(oldColor, myOutputColors[j]);
    }
    return dist;
  }

  private static double distance(double[] neighbor, double[] point) {
    double squares = 0;
    for (int i = 0; i < Math.min(neighbor.length, point.length); i++) {
      squares += Math.pow(neighbor[i] - point[i], 2);
    }
    return squares;
  }

  void getInitialColors() {
    HashSet<double[]> set = new HashSet<>();
    for (int step = 256; step > 0; step /= 2) {
      for (int i = 0; i < myInputDataSize; i += step) {
        double[] v = myData[i];
        set.add(v);
        if (set.size() == myK) {
          break;
        }
      }
      if (set.size() == myK) {
        break;
      }
    }
    double[][] c = set.toArray(new double[set.size()][3]);
    if (myOutputColors.length < set.size()) {
      myOutputColors = new double[set.size()][3];
    }
    System.arraycopy(c, 0, myOutputColors, 0, c.length);
  }
}
