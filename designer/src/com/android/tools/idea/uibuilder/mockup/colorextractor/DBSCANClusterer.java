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
import java.util.List;

/**
 * A Cluster using the DBSCAN algorithm.
 *
 * Based on the paper :
 *
 * A Density-Based Algorithm for Discovering Clusters
 * in Large Spatial Databases with Noise
 *
 * by:  Martin Ester, Hans-Peter Kriegel, Jörg Sander, Xiaowei Xu
 *
 * http://www2.cs.uh.edu/~ceick/7363/Papers/dbscan.pdf
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public class DBSCANClusterer implements Clusterer{

  private int myVisited = 0;

  private enum PointType {
    NOISE,
    BORDER,
    CORE
  }

  private float myEps;
  private int myMinPts;

  private List<List<double[]>> myClusters = new ArrayList<>();

  private PointType[] myPointTypes;
  ProgressListener listener;

  /**
   * Create a new DBSCAN clusterer
   *
   * @param eps    Minimum size between two point to be considered in the same cluster
   * @param minPts The minimum number of point needed to create a cluster
   */
  public DBSCANClusterer(float eps, int minPts) {
    myEps = eps;
    myMinPts = minPts;
  }

  /**
   * Create a new DBSCAN clusterer
   *
   * @param eps    Minimum size between two point to be considered in the same cluster
   * @param minPts The minimum number of point needed to create a cluster
   * @param listener listern to publish the progress of the algorithm
   */
  public DBSCANClusterer(float eps, int minPts, ProgressListener listener) {
    this(eps, minPts);
    this.listener = listener;
  }

  @Override
  public List<List<double[]>> cluster(double[][] input) {
    List<double[]> cluster = new ArrayList<>();
    myPointTypes = new PointType[input.length];

    for (int i = 0; i < input.length; i++) {
      if (myPointTypes[i] == null) { // If point is unclassified
        if (expandCluster(input, i, cluster)) {
          if (!cluster.isEmpty()) {
            myClusters.add(cluster);
            myVisited += cluster.size();
            notifyProgress();
          }
          cluster = new ArrayList<>();
        }
      }
    }
    return myClusters;
  }

  /**
   * Use the point at pointIndex as a a core point and find all other
   * points in its neighborhood.
   * @param input The data points
   * @param pointIndex The index of the point to expand the cluster from
   * @param cluster The cluster to expand
   * @return True if the cluster has been expanded
   */
  private boolean expandCluster(double[][] input, int pointIndex, List<double[]> cluster) {
    List<Integer> seeds = regionQuery(input, pointIndex);

    if (seeds.size() < myMinPts) {
      // Points are considered ad noise, we do not add them to the cluster
      myPointTypes[pointIndex] = PointType.NOISE;
      for (int i = 0; i < seeds.size(); i++) {
        myPointTypes[seeds.get(i)] = PointType.NOISE;
      }
      return false;
    }
    else {
      // The current point has a neighborhood so its a core and
      // all its neighbors are border points
      cluster.add(input[pointIndex]);
      myPointTypes[pointIndex] = PointType.CORE;
      for (int i = 0; i < seeds.size(); i++) {
        cluster.add(input[seeds.get(i)]);
        myPointTypes[seeds.get(i)] = PointType.BORDER;
      }

      // We now expand the search to all the neighbors,
      // if they have also have neighbors, they become core point
      while (!seeds.isEmpty()) {
        int currentP = seeds.get(0);
        List<Integer> result = regionQuery(input, currentP);

        if (result.size() >= myMinPts) {
          myPointTypes[currentP] = PointType.CORE;
          for (int i = 0; i < result.size(); i++) {
            Integer resultP = result.get(i);
            if (myPointTypes[resultP] == null || myPointTypes[resultP] == PointType.NOISE) {
              // if the point is not in a cluster yet
              if (myPointTypes[resultP] == null) {
                // if the point has never been visited, we add it to the neighborhood
                seeds.add(resultP);
              }
              myPointTypes[resultP] = PointType.BORDER;
              cluster.add(input[resultP]);
            }
          }
        }
        seeds.remove(0);
      }
      return true;
    }
  }

  private void notifyProgress() {
    if (listener != null) {
      listener.progress(myVisited / (float)myPointTypes.length);
    }
  }

  /**
   * Find all the point at a distance less or equal to eps.
   *
   * The distance measure is the euclidean distance.
   * @param input The input data.
   * @param pointIndex The index of the point in input that we have to find the neighbors of.
   * @return A list of index of the point that are neighbor of input[pointIndex]
   */
  private List<Integer> regionQuery(double[][] input, int pointIndex) {
    List<Integer> seeds = new ArrayList<>();
    double[] current = input[pointIndex];
    for (int i = 0; i < input.length; i++) {
      float epsSquare = myEps * myEps;
      if (input[i] != current && distance(input[i], current) <= epsSquare) {
        seeds.add(i);
      }
    }
    return seeds;
  }

  /**
   * Compute the square of the euclidean distance between neighbor and point
   * @param neighbor
   * @param point
   * @return the distance between neighbor and point
   */
  private static double distance(double[] neighbor, double[] point) {
    double squares = 0;
    for (int i = 0; i < Math.min(neighbor.length, point.length); i++) {
      squares += Math.pow(neighbor[i] - point[i], 2);
    }
    return squares;
  }
}
