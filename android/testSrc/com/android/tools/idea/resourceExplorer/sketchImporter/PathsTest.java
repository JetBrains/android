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

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchCurvePoint;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPoint2D;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchShapePath;
import org.junit.Ignore;
import org.junit.Test;

import java.awt.*;

import static com.android.tools.idea.resourceExplorer.sketchImporter.logic.PathDataUtils.*;
import static com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer.RECTANGLE_CLASS_TYPE;
import static com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer.SHAPE_PATH_CLASS_TYPE;
import static org.junit.Assert.assertEquals;

import java.awt.geom.Rectangle2D;

public class PathsTest {

  @Test
  public void obliqueLinePathTest() {

    SketchCurvePoint[] points = new SketchCurvePoint[]{
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.2, 0.7)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.8, 0.3))
    };

    SketchShapePath shapePath = new SketchShapePath(SHAPE_PATH_CLASS_TYPE,
                                                    "abc",
                                                    -1,
                                                    new Rectangle.Double(0, 0, 10, 10),
                                                    false,
                                                    false,
                                                    true,
                                                    "Line",
                                                    0,
                                                    false,
                                                    false,
                                                    points);

    Rectangle.Double frame = new Rectangle.Double(5, 3, 10, 10);

    assertEquals("M7.0,10.0 L13.0,6.0 ", buildGenericPathString(shapePath, frame));
  }

  @Test
  public void verticalLinePathTest() {

    SketchCurvePoint[] points = new SketchCurvePoint[]{
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.2, 0.7)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.2, 0.3))
    };

    SketchShapePath shapePath = new SketchShapePath(SHAPE_PATH_CLASS_TYPE,
                                                    "abc",
                                                    -1,
                                                    new Rectangle.Double(2, 2, 10, 10),
                                                    false,
                                                    false,
                                                    true,
                                                    "Line",
                                                    0,
                                                    false,
                                                    false,
                                                    points);

    Rectangle.Double frame = new Rectangle.Double(5, 3, 10, 10);

    assertEquals("M9.0,12.0 V8.0 ", buildGenericPathString(shapePath, frame));
  }

  @Test
  public void horizontalLinePathTest() {

    SketchCurvePoint[] points = new SketchCurvePoint[]{
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.2, 0.7)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.8, 0.7))
    };

    SketchShapePath shapePath = new SketchShapePath(SHAPE_PATH_CLASS_TYPE,
                                                    "abc",
                                                    -1,
                                                    new Rectangle.Double(3, 1, 10, 10),
                                                    false,
                                                    false,
                                                    true,
                                                    "Line",
                                                    0,
                                                    false,
                                                    false,
                                                    points);

    Rectangle.Double frame = new Rectangle.Double(5, 3, 10, 10);

    assertEquals("M10.0,11.0 H16.0 ", buildGenericPathString(shapePath, frame));
  }

  @Test
  @Ignore
  public void curvePathTest() {

    SketchCurvePoint[] points = new SketchCurvePoint[]{
      new SketchCurvePoint(0, 0, new SketchPoint2D(0.1, 0.2), (short)0, new SketchPoint2D(0, 0), true, false,
                           new SketchPoint2D(0.1, 0.5)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0.8, 0.1), (short)0, new SketchPoint2D(0.2, 0.1), true, true,
                           new SketchPoint2D(0.5, 0.1)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0.9, 0.2), false, true,
                           new SketchPoint2D(0.9, 0.5))
    };

    SketchShapePath shapePath = new SketchShapePath(SHAPE_PATH_CLASS_TYPE,
                                                    "abc",
                                                    -1,
                                                    new Rectangle.Double(1, 2, 10, 10),
                                                    false,
                                                    false,
                                                    true,
                                                    "Line",
                                                    0,
                                                    false,
                                                    true,
                                                    points);

    Rectangle.Double frame = new Rectangle.Double(5, 3, 10, 10);

    assertEquals("M7.0,10.0 C7.0,7.0 8.0,6.0 11.0,6.0 C14.0,6.0 15.0,7.0 15.0,10.0 z", buildGenericPathString(shapePath, frame));
  }

  @Test
  public void rectanglePathTest() {

    Rectangle.Double shapeGroupFrame = new Rectangle.Double(1, 26, 77, 20);
    Rectangle.Double frame = new Rectangle.Double(0, 0, 77, 20);

    assertEquals("M1.0,26.0 H78.0 V46.0 H1.0 z", buildRectanglePathString(frame, shapeGroupFrame));
  }

  @Test
  
  public void roundRectanglePathTest(){
    SketchCurvePoint[] points = new SketchCurvePoint[]{
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0, 0)),
      new SketchCurvePoint(0, 20, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(1, 0)),
      new SketchCurvePoint(0, 40, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(1, 1)),
      new SketchCurvePoint(0, 40, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0, 1))
    };

    SketchShapePath shapePath = new SketchShapePath(RECTANGLE_CLASS_TYPE,
                                                    "abc",
                                                    -1,
                                                    new Rectangle.Double(0, 0, 80, 100),
                                                    false,
                                                    false,
                                                    true,
                                                    "Line",
                                                    0,
                                                    false,
                                                    true,
                                                    points);

    Rectangle.Double frame = new Rectangle.Double(30, 20, 80, 100);

    assertEquals("M30.0,20.0 L90.0,20.0 Q110.0,20.0 110.0,40.0 L110.0,80.0 Q110.0,120.0 70.0,120.0 Q30.0,120.0 30.0,80.0 z", buildRoundRectanglePathString(shapePath, frame));
  }

  @Test
  public void singleShapePathTest(){
    Rectangle2D.Double frame = new Rectangle2D.Double(97,59,104,245);

    SketchCurvePoint[] points = new SketchCurvePoint[]{
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.5, 0)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(1, 1)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0, 1))
    };

    SketchShapePath shapePath = new SketchShapePath(SHAPE_PATH_CLASS_TYPE,
                                                    "abc",
                                                    -1,
                                                    new Rectangle.Double(0, 0, 104, 245),
                                                    false,
                                                    false,
                                                    true,
                                                    "Line",
                                                    0,
                                                    false,
                                                    true,
                                                    points);

    assertEquals("M149.0,59.0 L201.0,304.0 H97.0 z", buildSingleShapeString(shapePath, frame));
  }
}
