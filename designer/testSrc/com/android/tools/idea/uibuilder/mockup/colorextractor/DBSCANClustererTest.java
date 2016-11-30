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

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DBSCANClustererTest {

  static double[][] DATA = new double[][]{
    //                           eps     | 1 | 1
    //                           minSize | 1 | 0
    //                           ------------------
    new double[]{1., 1., 1.}, // cluster | 1 | 1
    new double[]{1., 2., 1.}, // N=noise | 1 | 1
    new double[]{1., 1., 2.}, //         | 1 | 1
    new double[]{0., 1., 1.}, //         | 1 | 1
    new double[]{4., 4., 2.}, //         | 2 | 2
    new double[]{4., 3., 3.}, //         | 2 | 3
    new double[]{4., 4., 4.}, //         | 2 | 4
    new double[]{4., 5., 4.}, //         | N | 4
    new double[]{10., 10., 10.} //       | N | 5
  };


  static double[][] DATA_2 = new double[][]{
    new double[]{0., 0., 0.},
    new double[]{10., 10., 10.},
    new double[]{0., 0., 10.},
    new double[]{10., 10., 0.},
  };


  @Test
  public void testCluster() throws Exception {
    DBSCANClusterer clusterer = new DBSCANClusterer(1, 1);
    List<List<double[]>> cluster = clusterer.cluster(DATA);
    assertNotNull(cluster);
    assertEquals(2, cluster.size());

    DBSCANClusterer clusterer2 = new DBSCANClusterer(1, 0);
    List<List<double[]>> cluster2 = clusterer2.cluster(DATA);
    assertNotNull(cluster2);
    assertEquals(5, cluster2.size());

    DBSCANClusterer clusterer3 = new DBSCANClusterer(1, 0);
    List<List<double[]>> cluster3 = clusterer3.cluster(DATA_2);
    assertNotNull(cluster3);
    assertEquals(4, cluster3.size());
  }
}