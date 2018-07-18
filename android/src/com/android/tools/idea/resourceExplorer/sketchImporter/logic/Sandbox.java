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

import static com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer.*;

import java.awt.*;

public class Sandbox {

  public static SketchStyle sketchStyle = new SketchStyle(new SketchBorderOptions(false, (short)2, (short)0),
                                                          new SketchBorder[]{
                                                            new SketchBorder(true,
                                                                             new Color(1, 0, 0, 1),
                                                                             (short)0,
                                                                             0,
                                                                             5)
                                                          },
                                                          new SketchFill[]{
                                                            new SketchFill(false,
                                                                           new Color(0, 1, 0, 1),
                                                                           (short)0)
                                                          },
                                                          (short)10,
                                                          (short)0);

  public static void main(String[] args) {
    checkRectangleStringBuild();
  }

  private static void checkLineStringBuild() {
    SketchCurvePoint[] points = new SketchCurvePoint[]{
      new SketchCurvePoint(0, 0, "0", (short)0, "0", false, false,
                           "{0.349989, 0.108385}"),
      new SketchCurvePoint(0, 0, "{0.967420, 0.457810}", (short)0, "{0.840633, 0.144571}", true, true,
                           "{0.904027, 0.301191}"),
      new SketchCurvePoint(0, 0, "{0.674224, 0.573126}", (short)0, "{0.526997, 0.503739}", true, true,
                           "{0.600610, 0.538433}"),
      new SketchCurvePoint(0, 0, "{0.784220, 1.073774}", (short)0, "{1.136359, 0.695013}", true, true,
                           "{0.960289, 0.884393}"),
      new SketchCurvePoint(0, 0, "{0, 0.533436}", (short)0, "{0, 1.024791}", true, true,
                           "{0, 0.779114}"),
      new SketchCurvePoint(0, 0, "{0.904027, 0.533436}", (short)0, "{0.389611, 0.140970}", true, true,
                           "{0.646819, 0.337203}"),
      new SketchCurvePoint(0, 0, "{0.349989, 0.809782}", (short)0, "{0.851232, 0.748445}", true, true,
                           "{0.600610, 0.779114}"),
      new SketchCurvePoint(0, 0, "{0,0}", (short)0, "{0.840633, 0.144571}", false, false,
                           "{0.5, 0}"),
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
      new SketchCurvePoint(0, 0, "0", (short)0, "0", false, false,
                           "{0.349989, 0.108385}"),
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
}
