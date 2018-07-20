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
package com.android.tools.idea.resourceExplorer.sketchImporter;

import com.android.tools.idea.resourceExplorer.sketchImporter.logic.StringPoint;
import com.android.tools.idea.resourceExplorer.sketchImporter.logic.VectorPathBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VectorPathBuilderTest {

  @Test
  public void createBezierCurveTest() {

    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    vectorPathBuilder.createBezierCurve(new StringPoint(1.0, 1.0),
                                        new StringPoint(2.0, 2.0),
                                        new StringPoint(3.0, 3.0));

    assertEquals("C1.0,1.0 2.0,2.0 3.0,3.0 ", vectorPathBuilder.getVectorString());
  }

  @Test
  public void createQuadCurveTest() {

    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    vectorPathBuilder.createQuadCurve(new StringPoint(1.0, 1.0),
                                        new StringPoint(2.0, 2.0));

    assertEquals("Q1.0,1.0 2.0,2.0 ", vectorPathBuilder.getVectorString());
  }

  @Test
  public void createHorizontalLineTest() {

    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    vectorPathBuilder.createHorizontalLine(new StringPoint(1.0, 2.0));

    assertEquals("H1.0 ", vectorPathBuilder.getVectorString());
  }

  @Test
  public void createVerticalLineTest() {

    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    vectorPathBuilder.createVerticalLine(new StringPoint(1.0, 2.0));

    assertEquals("V2.0 ", vectorPathBuilder.getVectorString());
  }

  @Test
  public void createLineTest() {

    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    vectorPathBuilder.createLine(new StringPoint(1.0, 2.0));

    assertEquals("L1.0,2.0 ", vectorPathBuilder.getVectorString());
  }

  @Test
  public void startPathTest() {

    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    vectorPathBuilder.startPath(new StringPoint(2.0, 2.0));

    assertEquals("M2.0,2.0 ", vectorPathBuilder.getVectorString());
  }
}
