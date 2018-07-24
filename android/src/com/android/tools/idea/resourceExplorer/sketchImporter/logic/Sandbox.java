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
package com.android.tools.idea.resourceExplorer.sketchImporter.logic;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.*;

import java.awt.*;

import static com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer.*;

public class Sandbox {

  public static SketchStyle sketchStyle = new SketchStyle(new SketchBorderOptions(false, (short)2, (short)0),
                                                          new SketchBorder[]{
                                                            new SketchBorder(true,
                                                                             new Color(1, 0, 0, 1),
                                                                             (short)0,
                                                                             0,
                                                                             5)
                                                          },
                                                          null, null, new SketchFill[]{
    new SketchFill(false,
                   new Color(0, 1, 0, 1),
                   null, (short)0, null, null, 0, 0, (short)0, 0)
  },
                                                          (short)10, null,
                                                          (short)0);

  public static void main(String[] args) {
    checkOvalStringBuild();
  }

  private static void checkLineStringBuild() {
    SketchCurvePoint[] points = new SketchCurvePoint[]{
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.35, 0.10)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0.96, 0.45), (short)0, new SketchPoint2D(0.84, 0.14), true, true,
                           new SketchPoint2D(0.9, 0.3)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0.67, 0.57), (short)0, new SketchPoint2D(0.52, 0.50), true, true,
                           new SketchPoint2D(0.6, 0.53)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0.78, 1.07), (short)0, new SketchPoint2D(1.13, 0.69), true, true,
                           new SketchPoint2D(0.96, 0.88)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0.53), (short)0, new SketchPoint2D(0, 1.02), true, true,
                           new SketchPoint2D(0, 0.77)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0.9, 0.53), (short)0, new SketchPoint2D(0.38, 0.14), true, true,
                           new SketchPoint2D(0.64, 0.33)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0.34, 0.8), (short)0, new SketchPoint2D(0.85, 0.74), true, true,
                           new SketchPoint2D(0.6, 0.77)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.5, 0)),
    };

    SketchLayer[] layers = new SketchLayer[]{
      new SketchShapePath(SHAPE_PATH_CLASS_TYPE,
                          "abc",
                          -1,
                          new Rectangle.Double(0, 0, 300.917472, 341.8355246),
                          false,
                          false,
                          true,
                          "Line",
                          0,
                          false,
                          false,
                          points)
    };

    SketchShapeGroup lineShapeGroup = new SketchShapeGroup(SHAPE_GROUP_CLASS_TYPE,
                                                           "abc",
                                                           -1,
                                                           new Rectangle.Double(43.8898527, 40.193431998, 300.917472, 341.8355246),
                                                           false,
                                                           false,
                                                           true,
                                                           "Line",
                                                           0,
                                                           false,
                                                           sketchStyle,
                                                           layers,
                                                           (short)0,
                                                           false,
                                                           (short)(0));

    String pathData = PathDataUtils.buildShapeString(lineShapeGroup);
    System.out.println(pathData);
  }

  private static void checkRectangleStringBuild() {
    SketchCurvePoint[] points = new SketchCurvePoint[]{
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.35, 0.1)),
    };


    SketchLayer[] layers = new SketchLayer[]{
      new SketchShapePath(RECTANGLE_CLASS_TYPE,
                          "abc",
                          -1,
                          new Rectangle.Double(0, 0, 77, 20),
                          false,
                          false,
                          true,
                          "Line",
                          0,
                          false,
                          false,
                          points)
    };

    SketchShapeGroup lineShapeGroup = new SketchShapeGroup(SHAPE_GROUP_CLASS_TYPE,
                                                           "abc",
                                                           -1,
                                                           new Rectangle.Double(1, 26, 77, 20),
                                                           false,
                                                           false,
                                                           true,
                                                           "Line",
                                                           0,
                                                           false,
                                                           sketchStyle,
                                                           layers,
                                                           (short)0,
                                                           false,
                                                           (short)(0));

    String pathData = PathDataUtils.buildShapeString(lineShapeGroup);
    System.out.println(pathData);
  }

  private static void checkOvalStringBuild() {
    SketchCurvePoint[] points = new SketchCurvePoint[]{
      new SketchCurvePoint(0, 0, new SketchPoint2D(0.77, 1), (short)0, new SketchPoint2D(0.22, 1), true, true,
                           new SketchPoint2D(0.5, 1)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(1, 0.22), (short)0, new SketchPoint2D(1, 0.77), true, true,
                           new SketchPoint2D(1, 0.5)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0.22, 0), (short)0, new SketchPoint2D(0.77, 0), true, true,
                           new SketchPoint2D(0.5, 0)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0.77), (short)0, new SketchPoint2D(0, 0.22), true, true,
                           new SketchPoint2D(0, 0.5))
    };

    SketchLayer[] layers = new SketchLayer[]{
      new SketchShapePath(OVAL_CLASS_TYPE,
                          "abc",
                          -1,
                          new Rectangle.Double(1, 2, 77, 16),
                          false,
                          false,
                          true,
                          "Line",
                          0,
                          false,
                          true,
                          points)
    };

    SketchShapeGroup lineShapeGroup = new SketchShapeGroup(SHAPE_GROUP_CLASS_TYPE,
                                                           "abc",
                                                           -1,
                                                           new Rectangle.Double(2, 73, 77, 16),
                                                           false,
                                                           false,
                                                           true,
                                                           "Line",
                                                           0,
                                                           false,
                                                           sketchStyle,
                                                           layers,
                                                           (short)0,
                                                           false,
                                                           (short)(0));

    String pathData = PathDataUtils.buildShapeString(lineShapeGroup);
    System.out.println(pathData);
  }

  private static void checkStarStringBuild() {
    SketchCurvePoint[] points = new SketchCurvePoint[]{
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.5, 0.85)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.25, 0.93)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.19, 0.67)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0, 0.5)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.19, 0.32)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.25, 0.06)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.5, 0.14)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.75, 0.06)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.8, 0.32)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(1, 0.5)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.8, 0.67)),
      new SketchCurvePoint(0, 0, new SketchPoint2D(0, 0), (short)0, new SketchPoint2D(0, 0), false, false,
                           new SketchPoint2D(0.75, 0.93))
    };

    SketchLayer[] layers = new SketchLayer[]{
      new SketchShapePath(STAR_CLASS_TYPE,
                          "abc",
                          -1,
                          new Rectangle.Double(0, 0, 260.5, 260.5),
                          false,
                          false,
                          true,
                          "Star",
                          0,
                          false,
                          true,
                          points)
    };

    SketchShapeGroup lineShapeGroup = new SketchShapeGroup(SHAPE_GROUP_CLASS_TYPE,
                                                           "abc",
                                                           -1,
                                                           new Rectangle.Double(70, 70, 260.5, 260.5),
                                                           false,
                                                           false,
                                                           true,
                                                           "Line",
                                                           0,
                                                           false,
                                                           sketchStyle,
                                                           layers,
                                                           (short)0,
                                                           false,
                                                           (short)(0));

    String pathData = PathDataUtils.buildShapeString(lineShapeGroup);
    System.out.println(pathData);
  }
}
