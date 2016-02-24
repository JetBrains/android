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
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.io.LineProcessor;
import com.google.common.io.Resources;
import com.intellij.openapi.diagnostic.Logger;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.util.glsl.ShaderCode;

import java.io.IOException;
import java.net.URL;

public class ShaderSource {
  private static final Logger LOG = Logger.getInstance(ShaderSource.class);

  private ShaderSource() {
  }

  public static Shader loadShader(GL2ES2 gl, String name) {
    return loadShader(gl, Resources.getResource(ShaderSource.class, "shaders/" + name + ".glsl"));
  }

  public static Shader loadShader(final GL2ES2 gl, URL resource) {
    try {
      Pair<String, String> source = Resources.readLines(resource, Charsets.US_ASCII, new LineProcessor<Pair<String, String>>() {
        private static final int MODE_COMMON = 0;
        private static final int MODE_VERTEX = 1;
        private static final int MODE_FRAGMENT = 2;
        // Preambles for GLSL 1.30 or later.
        private static final String VERTEX_130_PREAMBLE = "#define attribute in\n#define varying out\n";
        private static final String FRAGMENT_130_PREAMBLE =
          "#define varying in\nout vec4 mgl_FragColor;\n#define gl_FragColor mgl_FragColor\n#define texture2D texture\n";

        private final StringBuilder vertexSource = new StringBuilder();
        private final StringBuilder fragmentSource = new StringBuilder();

        /* Constructor */ {
          addPreamble();
        }

        private int mode = MODE_COMMON;

        @Override
        public boolean processLine(String line) throws IOException {
          line = line.trim();
          if ("//! COMMON".equals(line)) {
            mode = MODE_COMMON;
          }
          else if ("//! VERTEX".equals(line)) {
            mode = MODE_VERTEX;
          }
          else if ("//! FRAGMENT".equals(line)) {
            mode = MODE_FRAGMENT;
          }
          else if (!line.startsWith("//")) {
            switch (mode) {
              case MODE_COMMON:
                vertexSource.append(line).append('\n');
                fragmentSource.append(line).append('\n');
                break;
              case MODE_VERTEX:
                vertexSource.append(line).append('\n');
                break;
              case MODE_FRAGMENT:
                fragmentSource.append(line).append('\n');
                break;
            }
          }
          return true;
        }

        private void addPreamble() {
          String version = gl.getContext().getGLSLVersionString();
          String precision = ShaderCode.requiresDefaultPrecision(gl) ? "precision mediump float;\n" : "";

          if (gl.getContext().getGLSLVersionNumber().compareTo(GLContext.Version1_30) >= 0) {
            vertexSource.append(version).append(precision).append(VERTEX_130_PREAMBLE);
            fragmentSource.append(version).append(precision).append(FRAGMENT_130_PREAMBLE);
          }
        }

        @Override
        public Pair<String, String> getResult() {
          return Pair.of(vertexSource.toString(), fragmentSource.toString());
        }
      });

      Shader shader = new Shader(gl);
      if (!shader.link(source.getFirst(), source.getSecond())) {
        shader.delete();
        shader = null;
      }
      return shader;
    }
    catch (IOException e) {
      LOG.warn("Failed to load shader source", e);
      return null;
    }
  }
}
