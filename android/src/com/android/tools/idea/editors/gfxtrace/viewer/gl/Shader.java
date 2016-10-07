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
package com.android.tools.idea.editors.gfxtrace.viewer.gl;

import com.android.tools.idea.editors.gfxtrace.viewer.gl.Util.AttributeOrUniform;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;

import java.util.Arrays;
import java.util.Map;

public class Shader {
  private static Logger LOG = Logger.getInstance(Shader.class);

  private final GL2ES2 myGl;
  private final int myHandle;
  private final Map<String, Attribute> myAttributes = Maps.newHashMap();
  private final Map<String, Uniform> myUniforms = Maps.newHashMap();

  public Shader(GL2ES2 gl) {
    myGl = gl;
    myHandle = gl.glCreateProgram();
  }

  public boolean link(String vertexSource, String fragmentSource) {
    detachShaders();
    if (!attachShaders(vertexSource, fragmentSource) || !link()) {
      return false;
    }
    getAttributes();
    getUniforms();
    return true;
  }

  public void bind() {
    myGl.glUseProgram(myHandle);
  }

  /**
   * Allowed types are Float, Integer, int[] and float[].
   */
  public void setUniform(String name, Object value) {
    Uniform uniform = myUniforms.get(name);
    if (uniform != null && !uniform.set(value)) {
      LOG.warn("Unexpected uniform value: " + value + " (" + value.getClass() + ") for " + name);
    }
  }

  public void setAttribute(String name, float x, float y, float z) {
    Attribute attribute = myAttributes.get(name);
    if (attribute != null) {
      attribute.set(myGl, x, y, z);
    }
  }

  public void bindAttribute(String name, int elementSize, int elementType, int strideBytes, int offsetBytes) {
    Attribute attribute = myAttributes.get(name);
    if (attribute != null) {
      attribute.bind(myGl, elementSize, elementType, strideBytes, offsetBytes);
    }
  }

  public void unbindAttribute(String name) {
    Attribute attribute = myAttributes.get(name);
    if (attribute != null) {
      attribute.unbind(myGl);
    }
  }

  public void delete() {
    detachShaders();
    myGl.glDeleteProgram(myHandle);
    myAttributes.clear();
    myUniforms.clear();
  }

  private void detachShaders() {
    int[] shaders = Util.getAttachedShaders(myGl, myHandle);
    for (int i = 0; i < shaders.length; i++) {
      myGl.glDetachShader(myHandle, shaders[i]);
      myGl.glDeleteShader(shaders[i]);
    }
  }

  private boolean attachShaders(String vertexSource, String fragmentSource) {
    int vertexShader = createShader(myGl, GL2ES2.GL_VERTEX_SHADER, vertexSource);
    if (vertexShader < 0) {
      return false;
    }

    int fragmentShader = createShader(myGl, GL2ES2.GL_FRAGMENT_SHADER, fragmentSource);
    if (fragmentShader < 0) {
      myGl.glDeleteShader(vertexShader);
      return false;
    }

    myGl.glAttachShader(myHandle, vertexShader);
    myGl.glAttachShader(myHandle, fragmentShader);
    return true;
  }

  private boolean link() {
    myGl.glLinkProgram(myHandle);
    if (Util.getProgramiv(myGl, myHandle, GL2ES2.GL_LINK_STATUS) != GL.GL_TRUE) {
      LOG.warn("Failed to link program:\n" + Util.getProgramInfoLog(myGl, myHandle));
      return false;
    }
    return true;
  }

  private void getAttributes() {
    myAttributes.clear();
    for (AttributeOrUniform attribute : Util.getActiveAttributes(myGl, myHandle)) {
      myAttributes.put(attribute.name, new Attribute(attribute));
    }
  }

  private void getUniforms() {
    myUniforms.clear();
    for (AttributeOrUniform uniform : Util.getActiveUniforms(myGl, myHandle)) {
      myUniforms.put(uniform.name, new Uniform(myGl, uniform));
    }
  }

  private static int createShader(GL2ES2 gl, int type, String source) {
    int shader = gl.glCreateShader(type);
    gl.glShaderSource(shader, 1, new String[]{source}, null, 0);
    gl.glCompileShader(shader);
    if (Util.getShaderiv(gl, shader, GL2ES2.GL_COMPILE_STATUS) != GL.GL_TRUE) {
      LOG.warn("Failed to compile shader:\n" + Util.getShaderInfoLog(gl, shader) + "\n\nSource:\n" + source);
      gl.glDeleteShader(shader);
      return -1;
    }
    return shader;
  }

  private static class Attribute {
    private AttributeOrUniform myAttribute;

    public Attribute(AttributeOrUniform attribute) {
      myAttribute = attribute;
    }

    public void set(GL2ES2 gl, float x, float y, float z) {
      gl.glDisableVertexAttribArray(myAttribute.location);
      gl.glVertexAttrib3f(myAttribute.location, x, y, z);
    }

    public void bind(GL2ES2 gl, int elementSize, int elementType, int strideBytes, int offsetBytes) {
      gl.glEnableVertexAttribArray(myAttribute.location);
      gl.glVertexAttribPointer(myAttribute.location, elementSize, elementType, false, strideBytes, offsetBytes);
    }

    public void unbind(GL2ES2 gl) {
      gl.glDisableVertexAttribArray(myAttribute.location);
    }
  }

  private static class Uniform {
    private final AttributeOrUniform myUniform;
    private final Setter mySetter;

    public Uniform(GL2ES2 gl, AttributeOrUniform uniform) {
      myUniform = uniform;
      mySetter = getSetter(gl);
    }

    private Setter getSetter(final GL2ES2 gl) {
      final int location = myUniform.location;
      final int size = myUniform.size;

      switch (myUniform.type) {
        case GL.GL_SHORT:
        case GL.GL_UNSIGNED_INT:
        case GL.GL_FLOAT:
        case GL2ES2.GL_INT:
        case GL2ES2.GL_BOOL:
        case GL2ES2.GL_SAMPLER_2D:
        case GL2ES2.GL_SAMPLER_CUBE:
          return new Setter() {
            @Override
            public void set(float[] values) {
              gl.glUniform1fv(location, Math.min(size, values.length), values, 0);
            }

            @Override
            public void set(float value) {
              set(new float[]{value});
            }

            @Override
            public void set(int[] values) {
              gl.glUniform1iv(location, Math.min(size, values.length), values, 0);
            }

            @Override
            public void set(int value) {
              set(new int[]{value});
            }
          };
        case GL2ES2.GL_INT_VEC2:
        case GL2ES2.GL_BOOL_VEC2:
        case GL2ES2.GL_FLOAT_VEC2:
          return new Setter() {
            @Override
            public void set(float[] values) {
              gl.glUniform2fv(location, Math.min(size, values.length / 2), values, 0);
            }

            @Override
            public void set(float value) {
              set(new float[]{value});
            }

            @Override
            public void set(int[] values) {
              gl.glUniform2iv(location, Math.min(size, values.length / 2), values, 0);
            }

            @Override
            public void set(int value) {
              set(new int[]{value});
            }
          };
        case GL2ES2.GL_INT_VEC3:
        case GL2ES2.GL_BOOL_VEC3:
        case GL2ES2.GL_FLOAT_VEC3:
          return new Setter() {
            @Override
            public void set(float[] values) {
              gl.glUniform3fv(location, Math.min(size, values.length / 3), values, 0);
            }

            @Override
            public void set(float value) {
              set(new float[]{value});
            }

            @Override
            public void set(int[] values) {
              gl.glUniform3iv(location, Math.min(size, values.length / 3), values, 0);
            }

            @Override
            public void set(int value) {
              set(new int[]{value});
            }
          };
        case GL2ES2.GL_INT_VEC4:
        case GL2ES2.GL_BOOL_VEC4:
        case GL2ES2.GL_FLOAT_VEC4:
          return new Setter() {
            @Override
            public void set(float[] values) {
              gl.glUniform4fv(location, Math.min(size, values.length / 4), values, 0);
            }

            @Override
            public void set(float value) {
              set(new float[]{value});
            }

            @Override
            public void set(int[] values) {
              gl.glUniform4iv(location, Math.min(size, values.length / 4), values, 0);
            }

            @Override
            public void set(int value) {
              set(new int[]{value});
            }
          };
        case GL2ES2.GL_FLOAT_MAT2:
          return new Setter() {
            @Override
            public void set(float[] values) {
              gl.glUniformMatrix2fv(location, 1, false, values, 0);
            }

            @Override
            public void set(float value) {
              LOG.warn("Unexpected shader uniform value (expected mat2): " + value);
            }

            @Override
            public void set(int[] values) {
              LOG.warn("Unexpected shader uniform value (expected mat2): " + Arrays.toString(values));
            }

            @Override
            public void set(int value) {
              LOG.warn("Unexpected shader uniform value (expected mat2): " + value);
            }
          };
        case GL2ES2.GL_FLOAT_MAT3:
          return new Setter() {
            @Override
            public void set(float[] values) {
              gl.glUniformMatrix3fv(location, 1, false, values, 0);
            }

            @Override
            public void set(float value) {
              LOG.warn("Unexpected shader uniform value (expected mat3): " + value);
            }

            @Override
            public void set(int[] values) {
              LOG.warn("Unexpected shader uniform value (expected mat3): " + Arrays.toString(values));
            }

            @Override
            public void set(int value) {
              LOG.warn("Unexpected shader uniform value (expected mat3): " + value);
            }
          };
        case GL2ES2.GL_FLOAT_MAT4:
          return new Setter() {
            @Override
            public void set(float[] values) {
              gl.glUniformMatrix4fv(location, 1, false, values, 0);
            }

            @Override
            public void set(float value) {
              LOG.warn("Unexpected shader uniform value (expected mat4): " + value);
            }

            @Override
            public void set(int[] values) {
              LOG.warn("Unexpected shader uniform value (expected mat4): " + Arrays.toString(values));
            }

            @Override
            public void set(int value) {
              LOG.warn("Unexpected shader uniform value (expected mat4): " + value);
            }
          };
        default:
          LOG.warn("Unexpected shader uniform type: " + myUniform.type);
          throw new AssertionError();
      }
    }

    public boolean set(Object value) {
      if (value instanceof Float) {
        mySetter.set(((Float)value).floatValue());
      }
      else if (value instanceof Integer) {
        mySetter.set(((Integer)value).intValue());
      }
      else if (value instanceof int[]) {
        mySetter.set((int[])value);
      }
      else if (value instanceof float[]) {
        mySetter.set((float[])value);
      }
      else {
        return false;
      }
      return true;
    }

    private interface Setter {
      void set(int value);

      void set(int[] values);

      void set(float value);

      void set(float[] values);
    }
  }
}
