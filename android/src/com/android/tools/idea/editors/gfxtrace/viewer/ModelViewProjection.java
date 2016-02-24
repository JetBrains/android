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
package com.android.tools.idea.editors.gfxtrace.viewer;

import com.android.tools.idea.editors.gfxtrace.viewer.gl.Shader;
import com.android.tools.idea.editors.gfxtrace.viewer.vec.MatD;

import java.util.ArrayDeque;
import java.util.Deque;

public class ModelViewProjection {
  private final boolean myInvertNormals;
  private MatD myModelView = MatD.IDENTITY;
  private MatD myProjection = MatD.IDENTITY;
  private final Deque<MatD> myMatrixStack = new ArrayDeque<MatD>();

  public ModelViewProjection(boolean invertNormals) {
    this.myInvertNormals = invertNormals;
  }

  public void setProjection(MatD projection) {
    this.myProjection = projection;
  }

  public void setModelView(MatD modelView) {
    myModelView = modelView;
  }

  public void push(MatD transform) {
    myMatrixStack.push(myModelView);
    myModelView = myModelView.multiply(transform);
  }

  public void pop() {
    myModelView = myMatrixStack.pop();
  }

  public void apply(Shader shader) {
    shader.setUniform(Constants.MODEL_VIEW_UNIFORM, myModelView.toFloatArray());
    shader.setUniform(Constants.MODEL_VIEW_PROJECTION_UNIFORM, myProjection.multiply(myModelView).toFloatArray());
    shader.setUniform(Constants.NORMAL_MATRIX_UNIFORM, myModelView.toNormalMatrix(myInvertNormals));
    shader.setUniform(Constants.INVERT_NORMALS_UNIFORM, myInvertNormals ? -1f : 1f);
  }
}
