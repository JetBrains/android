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
package com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph;

/**
 * Base class for interpolator
 */
public abstract class Interpolator {

  public static final int SPLINE = 0;
  public static final int LINEAR = 1;

  public static Interpolator get(int type, double[] time, double[][] y) {
    if (type == SPLINE) {
      return new MonotoneSpline(time, y);
    }
    return new LinearInterpolator(time, y);
  }

  public abstract void getPos(double t, double[] v);

  public abstract void getPos(double t, float[] v);

  public abstract double getPos(double t, int j);

  public abstract void getSlope(double t, double[] v);

  public abstract double getSlope(double t, int j);

  public abstract double[] getTimePoints();
}
