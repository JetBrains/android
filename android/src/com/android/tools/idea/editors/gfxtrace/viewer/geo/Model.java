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

import com.android.tools.idea.editors.gfxtrace.service.gfxapi.GfxAPIProtos.DrawPrimitive;

public class Model {
  private final DrawPrimitive myPrimitive;
  private final float[] myPositions; // x, y, z
  private final float[] myNormals; // x, y, z
  private final int[] myIndices;
  private final BoundingBox myBounds = new BoundingBox();

  public Model(DrawPrimitive primitive, float[] positions, float[] normals, int[] indices) {
    myPrimitive = primitive;
    myPositions = positions;
    myNormals = normals;
    myIndices = indices;
    for (int i = 0; i < positions.length; i += 3) {
      myBounds.add(positions[i + 0], positions[i + 1], positions[i + 2]);
    }
  }

  public DrawPrimitive getPrimitive() {
    return myPrimitive;
  }

  public float[] getPositions() {
    return myPositions;
  }

  public float[] getNormals() {
    return myNormals;
  }

  public int[] getIndices() {
    return myIndices;
  }

  public BoundingBox getBounds() {
    return myBounds;
  }
}
