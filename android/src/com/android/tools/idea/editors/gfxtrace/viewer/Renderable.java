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
import com.jogamp.opengl.GL2ES2;

public interface Renderable {
  Renderable NOOP = new Renderable() {
    @Override
    public void init(GL2ES2 gl) {
    }

    @Override
    public void render(GL2ES2 gl, State state) {
     }

    @Override
    public void dispose(GL2ES2 gl) {
    }
  };

  void init(GL2ES2 gl);

  void render(GL2ES2 gl, State state);

  void dispose(GL2ES2 gl);

  class State {
    public final Shader shader;
    public final ModelViewProjection transform;

    public State(Shader shader, boolean invertNormals) {
      this.shader = shader;
      this.transform = new ModelViewProjection(invertNormals);
    }
  }
}
