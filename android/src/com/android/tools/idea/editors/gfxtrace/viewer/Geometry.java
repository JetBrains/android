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

import com.android.tools.idea.editors.gfxtrace.service.gfxapi.DrawPrimitive;
import com.android.tools.idea.editors.gfxtrace.viewer.camera.Emitter;
import com.android.tools.idea.editors.gfxtrace.viewer.geo.BoundingBox;
import com.android.tools.idea.editors.gfxtrace.viewer.geo.Model;
import com.android.tools.idea.editors.gfxtrace.viewer.gl.Buffer;
import com.android.tools.idea.editors.gfxtrace.viewer.vec.MatD;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;

public class Geometry {
  private Model myModel;
  private MatD myModelMatrix;
  private boolean myZUp;

  public Geometry() {
    setZUp(false);
  }

  public void setModel(Model model) {
    myModel = model;
    updateModelMatrix();
  }

  public Model getModel() {
    return myModel;
  }

  public void setZUp(boolean zUp) {
    myZUp = zUp;
    updateModelMatrix();
  }

  public BoundingBox getBounds() {
    if (myModel != null) {
      return myModel.getBounds();
    }
    return BoundingBox.INVALID;
  }

  private void updateModelMatrix() {
    myModelMatrix = getBounds().getCenteringMatrix(Constants.SCENE_SCALE_FACTOR, myZUp);
  }

  public Renderable asRenderable() {
    if (myModel == null) {
      return Renderable.NOOP;
    }
    final int modelPrimitive = translatePrimitive(myModel.getPrimitive());
    final float[] positions = myModel.getPositions();
    final float[] normals = myModel.getNormals();
    final int[] indices = myModel.getIndices();

    return new Renderable() {
      private Buffer positionBuffer;
      private Buffer normalBuffer;
      private Buffer indexBuffer;

      @Override
      public void init(GL2ES2 gl) {
        positionBuffer = new Buffer(gl, GL.GL_ARRAY_BUFFER).bind().loadData(Buffer.wrap(positions));
        if (normals != null) {
          normalBuffer = new Buffer(gl, GL.GL_ARRAY_BUFFER).bind().loadData(Buffer.wrap(normals));
        }
        indexBuffer = new Buffer(gl, GL.GL_ELEMENT_ARRAY_BUFFER).bind().loadData(Buffer.wrap(indices));
      }

      @Override
      public void render(GL2ES2 gl, State state) {
        state.transform.push(myModelMatrix);
        state.transform.apply(state.shader);

        positionBuffer.bind();
        state.shader.bindAttribute(Constants.POSITION_ATTRIBUTE, 3, GL.GL_FLOAT, 3 * 4, 0);
        if (normalBuffer != null) {
          normalBuffer.bind();
          state.shader.bindAttribute(Constants.NORMAL_ATTRIBUTE, 3, GL.GL_FLOAT, 3 * 4, 0);
        } else {
          state.shader.setAttribute(Constants.NORMAL_ATTRIBUTE, 1, 0, 0);
        }
        indexBuffer.bind();
        gl.glDrawElements(modelPrimitive, indices.length, GL.GL_UNSIGNED_INT, 0);
        state.shader.unbindAttribute(Constants.POSITION_ATTRIBUTE);
        if (normalBuffer != null) {
          state.shader.unbindAttribute(Constants.NORMAL_ATTRIBUTE);
        }

        state.transform.pop();
      }

      @Override
      public void dispose(GL2ES2 gl) {
        if (positionBuffer != null) {
          positionBuffer.delete();
          positionBuffer = null;
        }
        if (normalBuffer != null) {
          normalBuffer.delete();
          normalBuffer = null;
        }
        if (indexBuffer != null) {
          indexBuffer.delete();
          indexBuffer = null;
        }
      }
    };
  }

  public Emitter getEmitter() {
    return Emitter.BoxEmitter.fromBoundingBox(getBounds().transform(myModelMatrix));
  }

  private int translatePrimitive(DrawPrimitive primitive) {
    switch (primitive.getValue()) {
      case DrawPrimitive.PointsValue:
        return GL.GL_POINTS;
      case DrawPrimitive.LinesValue:
        return GL.GL_LINES;
      case DrawPrimitive.LineStripValue:
        return GL.GL_LINE_STRIP;
      case DrawPrimitive.LineLoopValue:
        return GL.GL_LINE_LOOP;
      case DrawPrimitive.TrianglesValue:
        return GL.GL_TRIANGLES;
      case DrawPrimitive.TriangleStripValue:
        return GL.GL_TRIANGLE_STRIP;
      case DrawPrimitive.TriangleFanValue:
        return GL.GL_TRIANGLE_FAN;
      default:
        throw new AssertionError();
    }
  }
}
