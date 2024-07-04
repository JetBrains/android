/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph.Easing;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph.Interpolator;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph.LinearInterpolator;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph.MonotoneSpline;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph.Oscillator;
import junit.framework.TestCase;

public class MotionEditorUtilsTest extends TestCase {

  public void testFramework_working() throws Exception {
    assertEquals(4, 2 + 2);
  }

  public void testCurveFit01() throws Exception {
    double[][] points = {
      {0, 0}, {1, 1}, {2, 0}
    };
    double[] time = {
      0, 5, 10
    };
    Interpolator spline = Interpolator.get(Interpolator.SPLINE, time, points);
    double value = spline.getPos(5, 0);
    assertEquals(1, value, 0.001);
    value = spline.getPos(7, 0);
    assertEquals(1.4, value, 0.001);
    value = spline.getPos(7, 1);
    assertEquals(0.744, value, 0.001);

    assertEquals(0.2, spline.getSlope(5, 0), 0.001);
    assertEquals(0.2, spline.getSlope(7, 0), 0.001);
    assertEquals(-0.224, spline.getSlope(7, 1), 0.001);

    double[] val = new double[2];
    spline.getSlope(7, val);
    assertEquals(0.2, val[0], 0.001);
    assertEquals(-0.224, val[1], 0.001);
    spline.getPos(7, val);
    assertEquals(1.4, val[0], 0.001);
    assertEquals(0.744, val[1], 0.001);
  }

  public void testCurveFit03() throws Exception {
    double[][] points = {
      {0, 0}, {1, 1}, {2, 0}
    };
    double[] time = {
      0, 5, 10
    };
    MonotoneSpline spline = new MonotoneSpline(time, points);
    double value = spline.getPos(5, 0);
    assertEquals(1, value, 0.001);
    value = spline.getPos(7, 0);
    assertEquals(1.4, value, 0.001);
    value = spline.getPos(7, 1);
    assertEquals(0.744, value, 0.001);

    assertEquals(0.2, spline.getSlope(5, 0), 0.001);
    assertEquals(0.2, spline.getSlope(7, 0), 0.001);
    assertEquals(-0.224, spline.getSlope(7, 1), 0.001);

    double[] val = new double[2];
    spline.getSlope(7, val);
    assertEquals(0.2, val[0], 0.001);
    assertEquals(-0.224, val[1], 0.001);
    spline.getPos(7, val);
    assertEquals(1.4, val[0], 0.001);
    assertEquals(0.744, val[1], 0.001);
    float[] fval = new float[2];

    spline.getPos(7, fval);
    assertEquals(1.4, fval[0], 0.001);
    assertEquals(0.744, fval[1], 0.001);
    assertEquals(0.0, spline.getLength2D(0), 0.001);
    assertEquals(-0.734666, spline.getLength2D(0.5), 0.001);
    assertEquals(-0.685, spline.getLength2D(0.3), 0.001);
    assertEquals(-0.6686, spline.getLength2D(2), 0.001);
    assertEquals(2.35173, spline.getLength2D(8), 0.001);
    assertEquals(0.2741, MonotoneSpline.dumbLength(spline, 0.1), 0.001);
    assertEquals(0.29028, MonotoneSpline.dumbLength(spline, 0.05), 0.001);
    assertEquals(0.30649, MonotoneSpline.dumbLength(spline, 0.001), 0.001);
  }

  public void testCurveFit04() throws Exception {
    double[][] points = {
      {0, 0}, {1, 1}, {2, 0}
    };
    double[] time = {
      0, 5, 10
    };
    LinearInterpolator spline = new LinearInterpolator(time, points);
    double value = spline.getPos(5, 0);
    assertEquals(1, value, 0.001);
    value = spline.getPos(7, 0);
    assertEquals(1.4, value, 0.001);
    value = spline.getPos(7, 1);
    assertEquals(0.6, value, 0.001);

    assertEquals(0.2, spline.getSlope(5, 0), 0.001);
    assertEquals(0.2, spline.getSlope(7, 0), 0.001);
    assertEquals(-0.2, spline.getSlope(7, 1), 0.001);

    double[] val = new double[2];
    spline.getSlope(7, val);
    assertEquals(0.2, val[0], 0.001);
    assertEquals(-0.2, val[1], 0.001);
    spline.getPos(7, val);
    assertEquals(1.4, val[0], 0.001);
    assertEquals(0.6, val[1], 0.001);
    float[] fval = new float[2];

    spline.getPos(7, fval);
    assertEquals(1.4, fval[0], 0.001);
    assertEquals(0.6, fval[1], 0.001);
  }

  public void testCurveFit02() throws Exception {
    double[][] points = {
      {0, 0}, {1, 1}, {2, 0}
    };
    double[] time = {
      0, 5, 10
    };
    Interpolator spline = Interpolator.get(Interpolator.LINEAR, time, points);
    double value = spline.getPos(5, 0);
    assertEquals(1, value, 0.001);
    value = spline.getPos(7, 0);
    assertEquals(1.4, value, 0.001);
    value = spline.getPos(7, 1);
    assertEquals(0.6, value, 0.001);
  }

  public void testEasing01() throws Exception {
    double value, diffValue;
    Easing easing;
    easing = Easing.getInterpolator("cubic=(1,1,0,0)");
    value = easing.get(0.5);
    assertEquals(0.5, value, 0.001);
    diffValue = easing.getDiff(0.5);
    assertEquals(1, diffValue, 0.001);
    diffValue = easing.getDiff(0.1);
    assertEquals(1, diffValue, 0.001);
    diffValue = easing.getDiff(0.9);
    assertEquals(1, diffValue, 0.001);

    easing = Easing.getInterpolator("cubic=(1,0,0,1)");
    value = easing.get(0.5);
    assertEquals(0.5, value, 0.001);

    diffValue = easing.getDiff(0.001);
    assertEquals(0, diffValue, 0.001);
    diffValue = easing.getDiff(0.9999);
    assertEquals(0, diffValue, 0.001);

    easing = Easing.getInterpolator("cubic=(0.5,1,0.5,0)");
    value = easing.get(0.5);
    assertEquals(0.5, value, 0.001);
    diffValue = easing.getDiff(0.5);
    assertEquals(0, diffValue, 0.001);
    diffValue = easing.getDiff(0.00001);
    assertEquals(2, diffValue, 0.001);
    diffValue = easing.getDiff(0.99999);
    assertEquals(2, diffValue, 0.001);

    easing = Easing.getInterpolator(Easing.ACCELERATE_NAME);
    assertEquals(0.32173, easing.get(0.5), 0.001);
    easing = Easing.getInterpolator(Easing.DECELERATE_NAME);
    assertEquals(0.8179, easing.get(0.5), 0.001);
    easing = Easing.getInterpolator(Easing.LINEAR_NAME);
    assertEquals(0.5, easing.get(0.5), 0.001);
    easing = Easing.getInterpolator(Easing.STANDARD_NAME);
    assertEquals(0.7753, easing.get(0.5), 0.001);
    easing = new Easing.CubicEasing(0.5, 1, 0.5, 0);
    value = easing.get(0.5);
    assertEquals(0.5, value, 0.001);
    easing = new Easing.CubicEasing("cubic=(0.5,1,0.5,0)");
    value = easing.get(0.5);
    assertEquals(0.5, value, 0.001);
  }

  public void testLinearCurveFit01() throws Exception {
    double value, diffValue;
    double[][] points = {
      {0, 0}, {1, 1}, {2, 0}
    };
    double[] time = {
      0, 5, 10
    };
    LinearInterpolator lcurve = new LinearInterpolator(time, points);
    value = lcurve.getPos(5, 0);
    assertEquals(1, value, 0.001);
    value = lcurve.getPos(7, 0);
    assertEquals(1.4, value, 0.001);
    value = lcurve.getPos(7, 1);
    assertEquals(0.6, value, 0.001);

    assertEquals(0.2, lcurve.getSlope(5, 0), 0.001);
    assertEquals(0.2, lcurve.getSlope(7, 0), 0.001);
    assertEquals(-0.2, lcurve.getSlope(7, 1), 0.001);
  }

  public void testOscillator01() throws Exception {
    Oscillator o = new Oscillator();
    o.setType(Oscillator.SQUARE_WAVE);
    o.addPoint(0, 0);
    o.addPoint(0.5, 10);
    o.addPoint(1, 0);
    o.normalize();
    assertEquals(19, countZeroCrossings(o, Oscillator.SIN_WAVE));
    assertEquals(19, countZeroCrossings(o, Oscillator.SQUARE_WAVE));
    assertEquals(19, countZeroCrossings(o, Oscillator.TRIANGLE_WAVE));
    assertEquals(19, countZeroCrossings(o, Oscillator.SAW_WAVE));
    assertEquals(19, countZeroCrossings(o, Oscillator.REVERSE_SAW_WAVE));
    assertEquals(20, countZeroCrossings(o, Oscillator.COS_WAVE));
  }

  private int countZeroCrossings(Oscillator o, int type) {
    int n = 1000;
    double last = o.getValue(0);
    int count = 0;
    o.setType(type);
    for (int i = 0; i < n; i++) {

      double v = o.getValue(0.0001 + i / (double)n);
      if (v * last < 0) {
        count++;
      }
      last = v;
    }
    return count;
  }

}
