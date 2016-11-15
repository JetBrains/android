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
package com.android.tools.sherpa.scout;

import android.support.constraint.solver.widgets.ConstraintAnchor;
import android.support.constraint.solver.widgets.ConstraintWidget;
import android.support.constraint.solver.widgets.ConstraintWidgetContainer;
import com.android.tools.sherpa.structure.WidgetsScene;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This test the major methods of the Scout subsystem
 */
public class ScoutTest extends TestCase {
  static int[][] testVertical = {
    {100, 100, 300, 400},
    {400, 130, 500, 300},
  };

  public void testTestFramework() {
    ConstraintWidgetContainer root = buildTestLayout(testVertical);
    assertEquals(rectToString(testVertical), widgetsToRect(root.getChildren()));
  }

  public void testAlignVerticallyTop() {
    ConstraintWidgetContainer root = buildTestLayout(testVertical);
    Scout.arrangeWidgets(Scout.Arrange.AlignVerticallyTop, root.getChildren(), false);
    int[][] result = new int[][]{
      {100, 100, 300, 400},
      {400, 100, 500, 300},
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  public void testAlignVerticallyMiddle() {

    ConstraintWidgetContainer root = buildTestLayout(testVertical);

    Scout.arrangeWidgets(Scout.Arrange.AlignVerticallyMiddle, root.getChildren(), false);
    int[][] result = new int[][]{
      {100, 90, 300, 400},
      {400, 140, 500, 300},
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  public void testAlignVerticallyBottom() {

    ConstraintWidgetContainer root = buildTestLayout(testVertical);

    Scout.arrangeWidgets(Scout.Arrange.AlignVerticallyBottom, root.getChildren(), false);
    int[][] result = new int[][]{
      {100, 100, 300, 400},
      {400, 200, 500, 300},
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  static int[][] testHorizontal = {
    {100, 100, 300, 400},
    {130, 400, 500, 300},
  };


  public void testAlignHorizontallyLeft() {
    ConstraintWidgetContainer root = buildTestLayout(testHorizontal);
    Scout.arrangeWidgets(Scout.Arrange.AlignHorizontallyLeft, root.getChildren(), false);
    int[][] result = new int[][]{
      {100, 100, 300, 400},
      {100, 400, 500, 300},
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  public void testAlignHorizontallyCenter() {
    ConstraintWidgetContainer root = buildTestLayout(testHorizontal);
    Scout.arrangeWidgets(Scout.Arrange.AlignHorizontallyCenter, root.getChildren(), false);
    int[][] result = new int[][]{
      {165, 100, 300, 400},
      {65, 400, 500, 300},
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  public void testAlignHorizontallyRight() {
    ConstraintWidgetContainer root = buildTestLayout(testHorizontal);
    Scout.arrangeWidgets(Scout.Arrange.AlignHorizontallyRight, root.getChildren(), false);
    int[][] result = new int[][]{
      {330, 100, 300, 400},
      {130, 400, 500, 300},
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  static int[][] testDistribute = {
    {100, 100, 100, 100},
    {300, 300, 100, 100},
    {500, 500, 100, 100},
    {700, 700, 100, 100},
  };


  public void testDistributeVertically() {
    ConstraintWidgetContainer root = buildTestLayout(testDistribute);
    Scout.arrangeWidgets(Scout.Arrange.DistributeVertically, root.getChildren(), false);
    int[][] result = new int[][]{
      {100, 100, 100, 100},
      {300, 300, 100, 100},
      {500, 500, 100, 100},
      {700, 700, 100, 100},
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  public void testDistributeHorizontally() {
    ConstraintWidgetContainer root = buildTestLayout(testDistribute);
    Scout.arrangeWidgets(Scout.Arrange.DistributeHorizontally, root.getChildren(), false);
    int[][] result = new int[][]{
      {100, 100, 100, 100},
      {300, 300, 100, 100},
      {500, 500, 100, 100},
      {700, 700, 100, 100},
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }


  public void testExpandVertically() {
    ConstraintWidgetContainer root = buildTestLayout(testDistribute);
    Scout.arrangeWidgets(Scout.Arrange.ExpandVertically, root.getChildren(), false);
    int[][] result = new int[][]{
      {100, 8, 100, 984}, {300, 8, 100, 984}, {500, 8, 100, 984}, {700, 8, 100, 984}
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  public void testExpandHorizontally() {
    ConstraintWidgetContainer root = buildTestLayout(testDistribute);
    Scout.arrangeWidgets(Scout.Arrange.ExpandHorizontally, root.getChildren(), false);
    int[][] result = new int[][]{
      {8, 100, 984, 100}, {8, 300, 984, 100}, {8, 500, 984, 100}, {8, 700, 984, 100}
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }


  public void testVerticalPack() {
    ConstraintWidgetContainer root = buildTestLayout(testDistribute);
    Scout.arrangeWidgets(Scout.Arrange.VerticalPack, root.getChildren(), false);
    int[][] result = new int[][]{
      {100, 100, 100, 100}, {300, 100, 100, 100}, {500, 100, 100, 100}, {700, 100, 100, 100}
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  public void testHorizontalPack() {
    ConstraintWidgetContainer root = buildTestLayout(testDistribute);
    Scout.arrangeWidgets(Scout.Arrange.HorizontalPack, root.getChildren(), false);
    int[][] result = new int[][]{
      {100, 100, 100, 100}, {100, 300, 100, 100}, {100, 500, 100, 100}, {100, 700, 100, 100}
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }


  static int[][] testCenterHorizontal = {
    {100, 100, 100, 100},
    {300, 100, 100, 100},
    {500, 100, 100, 100},
    {700, 100, 100, 100},
  };
  static int[][] testCenterVertical = {
    {100, 100, 100, 100},
    {100, 300, 100, 100},
    {300, 500, 100, 100},
    {400, 700, 100, 100},
  };

  public void testCenterHorizontallyInParent() {
    ConstraintWidgetContainer root = buildTestLayout(testCenterHorizontal);
    Scout.arrangeWidgets(Scout.Arrange.CenterHorizontallyInParent, root.getChildren(), false);
    int[][] result = new int[][]{
      {450, 100, 100, 100}, {450, 100, 100, 100}, {450, 100, 100, 100}, {450, 100, 100, 100}
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  public void testCenterVerticallyInParent() {
    ConstraintWidgetContainer root = buildTestLayout(testCenterVertical);
    Scout.arrangeWidgets(Scout.Arrange.CenterVerticallyInParent, root.getChildren(), false);
    int[][] result = new int[][]{
      {100, 450, 100, 100}, {100, 450, 100, 100}, {300, 450, 100, 100}, {400, 450, 100, 100}
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  public void testCenterVertically() {
    ConstraintWidgetContainer root = buildTestLayout(testCenterVertical);
    Scout.arrangeWidgets(Scout.Arrange.CenterVertically, root.getChildren(), false);
    int[][] result = new int[][]{
      {100, 100, 100, 100}, {100, 550, 100, 100}, {300, 450, 100, 100}, {400, 450, 100, 100}
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  public void testCenterHorizontally() {
    ConstraintWidgetContainer root = buildTestLayout(testCenterHorizontal);
    Scout.arrangeWidgets(Scout.Arrange.CenterHorizontally, root.getChildren(), false);
    int[][] result = new int[][]{
      {100, 100, 100, 100}, {300, 100, 100, 100}, {500, 100, 100, 100}, {750, 100, 100, 100}
    };
    assertEquals(rectToString(result), widgetsToRect(root.getChildren()));
  }

  static int[][] infer = {
    {100, 100, 100, 100},
    {800, 800, 100, 100},
  };

  public void testInferConstraints() {
    ConstraintWidgetContainer root = buildTestLayout(infer);
    WidgetsScene scene = new WidgetsScene();
    scene.setRoot(root);
    Scout.inferConstraints(scene);

    assertEquals("(LL100 TT100) (RR100 BB100)", toString(root));
  }

  String toString(ConstraintWidgetContainer root) {
    ArrayList<ConstraintWidget> list = root.getChildren();
    String s = "";
    for (ConstraintWidget widget : list) {
      s += "(";
      ArrayList<ConstraintAnchor> alist = widget.getAnchors();
      for (ConstraintAnchor anchor : alist) {
        if (anchor.isConnected()) {
          s += anchor.getType().toString().substring(0, 1);
          s += anchor.getTarget().getType().toString().substring(0, 1);
          ;
          s += anchor.getMargin() + " ";
        }
      }
      s = s.trim();
      s += ") ";
    }
    return s.trim();
  }

  public void testWrap() {
    ConstraintWidgetContainer root = buildTestLayout(testCenterHorizontal);
    Scout.wrap(root);
    int[][] result = new int[][]{
      {0, 0, 716, 116}
    };
    List<ConstraintWidget> widget = Arrays.asList(new ConstraintWidget[]{root});
    assertEquals(rectToString(result), widgetsToRect(widget));
  }

  private String rectToString(int[][] recs) {
    String ret = "";
    for (int i = 0; i < recs.length; i++) {
      if (i != 0) {
        ret += ", ";
      }
      ret += (Arrays.toString(recs[i]).replace("[", "{").replace("]", "}"));
    }
    return ret;
  }

  private String widgetsToRect(List<ConstraintWidget> widgets) {
    int[][] ret = new int[widgets.size()][4];
    for (int i = 0; i < ret.length; i++) {
      ret[i][0] = widgets.get(i).getLeft();
      ret[i][1] = widgets.get(i).getTop();
      ret[i][2] = widgets.get(i).getWidth();
      ret[i][3] = widgets.get(i).getHeight();
    }
    return rectToString(ret);
  }

  ConstraintWidgetContainer buildTestLayout(int[][] recs) {
    ConstraintWidgetContainer root = new ConstraintWidgetContainer(0, 0, 1000, 1000);
    for (int i = 0; i < recs.length; i++) {
      ConstraintWidget widget = new ConstraintWidget();
      widget.setType("TextView");
      String text = ("W" + i);
      widget.setDebugName(text);
      widget.setOrigin(recs[i][0], recs[i][1]);
      widget.setWidth(recs[i][2]);
      widget.setHeight(recs[i][3]);
      widget.setDrawWidth(recs[i][2]);
      widget.setDrawHeight(recs[i][3]);
      widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
      widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
      widget.setDimension(recs[i][2], recs[i][3]);
      widget.setWrapHeight(recs[i][2]);
      widget.setWrapHeight(recs[i][3]);
      root.add(widget);
    }

    return root;
  }
}
