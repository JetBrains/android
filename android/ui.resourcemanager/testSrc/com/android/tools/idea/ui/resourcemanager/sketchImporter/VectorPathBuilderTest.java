/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.sketchImporter;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.builders.PathStringBuilder;
import org.junit.Test;

public class VectorPathBuilderTest {

  @Test
  public void createBezierCurveTest() {
    PathStringBuilder pathStringBuilder = new PathStringBuilder();
    double[] coordinates = {1.0, 1.0, 2.0, 2.0, 3.0, 3.0};

    pathStringBuilder.createBezierCurve(coordinates);

    assertEquals("C1,1 2,2 3,3 ", pathStringBuilder.build());
  }

  @Test
  public void createQuadCurveTest() {
    PathStringBuilder pathStringBuilder = new PathStringBuilder();

    pathStringBuilder.createQuadCurve(1.0, 1.0,
                                      2.0, 2.0);

    assertEquals("Q1,1 2,2 ", pathStringBuilder.build());
  }

  @Test
  public void createLineTest() {
    PathStringBuilder pathStringBuilder = new PathStringBuilder();

    pathStringBuilder.createLine(1.0, 2.0);

    assertEquals("L1,2 ", pathStringBuilder.build());
  }

  @Test
  public void startPathTest() {
    PathStringBuilder pathStringBuilder = new PathStringBuilder();

    pathStringBuilder.startPath(2.0, 2.0);

    assertEquals("M2,2 ", pathStringBuilder.build());
  }
}
