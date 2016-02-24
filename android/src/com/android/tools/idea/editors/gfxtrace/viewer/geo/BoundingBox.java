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
package com.android.tools.idea.editors.gfxtrace.viewer.geo;

import com.android.tools.idea.editors.gfxtrace.viewer.vec.MatD;
import com.android.tools.idea.editors.gfxtrace.viewer.vec.VecD;

public class BoundingBox {
  public static final BoundingBox INVALID = new BoundingBox();

  public final double[] min = new double[] { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
  public final double[] max = new double[] { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };

  public void add(VecD vec) {
    add(vec.x, vec.y, vec.z);
  }

  public void add(double x, double y, double z) {
    VecD.min(min, x, y, z);
    VecD.max(max, x, y, z);
  }

  public MatD getCenteringMatrix(double diagonalSize, boolean zUp) {
    VecD min = VecD.fromArray(this.min), max = VecD.fromArray(this.max);
    double diagonal = max.distance(min);

    VecD translation = max.subtract(min).scale(0.5f).add(min).scale(-1);
    double scale = (diagonal == 0) ? 1 : diagonalSize / diagonal;

    return zUp ? MatD.makeScaleTranslationZupToYup(scale, translation) : MatD.makeScaleTranslation(scale, translation);
  }

  public BoundingBox transform(MatD transform) {
    VecD tMin = transform.multiply(VecD.fromArray(min)), tMax = transform.multiply(VecD.fromArray(max));
    BoundingBox result = new BoundingBox();
    result.add(tMin);
    result.add(tMax);
    return result;
  }
}
