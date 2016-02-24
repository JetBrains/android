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

import com.google.common.base.Charsets;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;

public class Util {
  private Util() {
  }

  public static int createBuffer(GL gl) {
    int[] result = new int[1];
    gl.glGenBuffers(1, result, 0);
    return result[0];
  }

  public static int getShaderiv(GL2ES2 gl, int shader, int name) {
    int[] result = new int[1];
    gl.glGetShaderiv(shader, name, result, 0);
    return result[0];
  }

  public static int getProgramiv(GL2ES2 gl, int program, int name) {
    int[] result = new int[1];
    gl.glGetProgramiv(program, name, result, 0);
    return result[0];
  }

  public static String getShaderInfoLog(GL2ES2 gl, int shader) {
    int length = getShaderiv(gl, shader, GL2ES2.GL_INFO_LOG_LENGTH);
    byte[] log = new byte[length + 2];
    gl.glGetShaderInfoLog(shader, length, new int[1], 0, log, 0);
    for (length = log.length - 1; length >= 0 && log[length] == 0; length--) { }
    return new String(log, 0, length + 1, Charsets.US_ASCII);
  }

  public static String getProgramInfoLog(GL2ES2 gl, int program) {
    int length = getProgramiv(gl, program, GL2ES2.GL_INFO_LOG_LENGTH);
    byte[] log = new byte[length + 2];
    gl.glGetProgramInfoLog(program, length, new int[1], 0, log, 0);
    for (length = log.length - 1; length >= 0 && log[length] == 0; length--) { }
    return new String(log, 0, length + 1, Charsets.US_ASCII);
  }

  public static int[] getAttachedShaders(GL2ES2 gl, int program) {
    int numShaders = getProgramiv(gl, program, GL2ES2.GL_ATTACHED_SHADERS);
    if (numShaders > 0) {
      int[] shaders = new int[numShaders], count = new int[1];
      gl.glGetAttachedShaders(program, numShaders, count, 0, shaders, 0);
      return shaders;
    }
    return new int[0];
  }

  public static AttributeOrUniform[] getActiveAttributes(GL2ES2 gl, int program) {
    int maxAttributeNameLength = getProgramiv(gl, program, GL2ES2.GL_ACTIVE_ATTRIBUTE_MAX_LENGTH);
    int numAttributes = getProgramiv(gl, program, GL2ES2.GL_ACTIVE_ATTRIBUTES);
    int[] length = new int[1], size = new int[1], type = new int[1];
    byte[] name = new byte[maxAttributeNameLength];

    AttributeOrUniform[] result = new AttributeOrUniform[numAttributes];
    for (int i = 0; i < numAttributes; i++) {
      gl.glGetActiveAttrib(program, i, maxAttributeNameLength, length, 0, size, 0, type, 0, name, 0);
      String nameString = new String(name, 0, length[0], Charsets.US_ASCII);
      result[i] = new AttributeOrUniform(gl.glGetAttribLocation(program, nameString), nameString, type[0], size[0]);
    }
    return result;
  }

  public static AttributeOrUniform[] getActiveUniforms(GL2ES2 gl, int program) {
    int maxUniformNameLength = getProgramiv(gl, program, GL2ES2.GL_ACTIVE_UNIFORM_MAX_LENGTH);
    int numUniforms = getProgramiv(gl, program, GL2ES2.GL_ACTIVE_UNIFORMS);
    int[] length = new int[1], size = new int[1], type = new int[1];
    byte[] name = new byte[maxUniformNameLength];

    AttributeOrUniform[] result = new AttributeOrUniform[numUniforms];
    for (int i = 0; i < numUniforms; i++) {
      gl.glGetActiveUniform(program, i, maxUniformNameLength, length, 0, size, 0, type, 0, name, 0);
      String nameString = new String(name, 0, length[0], Charsets.US_ASCII);
      if (nameString.endsWith("[0]")) {
        nameString = nameString.substring(0, nameString.length() - 3);
      }
      result[i] = new AttributeOrUniform(gl.glGetUniformLocation(program, nameString), nameString, type[0], size[0]);
    }
    return result;
  }

  public static class AttributeOrUniform {
    public final int location;
    public final String name;
    public final int type;
    public final int size;

    public AttributeOrUniform(int location, String name, int type, int size) {
      this.location = location;
      this.name = name;
      this.type = type;
      this.size = size;
    }
  }
}
