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

import com.jogamp.opengl.GL;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Buffer {
  private final GL myGl;
  private final int myTarget;
  private final int myHandle;
  private int mySize;

  public Buffer(GL gl, int target) {
    myGl = gl;
    myTarget = target;
    myHandle = Util.createBuffer(gl);
  }

  public Buffer bind() {
    myGl.glBindBuffer(myTarget, myHandle);
    return this;
  }

  public Buffer loadData(ByteBuffer buffer) {
    return loadData(buffer, GL.GL_STATIC_DRAW);
  }

  public Buffer loadData(ByteBuffer buffer, int usage) {
    this.mySize = buffer.remaining();
    myGl.glBufferData(myTarget, mySize, buffer, usage);
    return this;
  }

  public int getSize() {
    return mySize;
  }

  public void delete() {
    myGl.glDeleteBuffers(1, new int[]{myHandle}, 0);
  }

  public static ByteBuffer wrap(byte[] data) {
    return ByteBuffer.wrap(data);
  }

  public static ByteBuffer wrap(short[] data) {
    ByteBuffer result = ByteBuffer.allocate(data.length * 2);
    result.order(ByteOrder.nativeOrder());
    result.asShortBuffer().put(data);
    result.rewind();
    return result;
  }

  public static ByteBuffer wrap(int[] data) {
    ByteBuffer result = ByteBuffer.allocate(data.length * 4);
    result.order(ByteOrder.nativeOrder());
    result.asIntBuffer().put(data);
    result.rewind();
    return result;
  }

  public static ByteBuffer wrap(float[] data) {
    ByteBuffer result = ByteBuffer.allocate(data.length * 4);
    result.order(ByteOrder.nativeOrder());
    result.asFloatBuffer().put(data);
    result.rewind();
    return result;
  }
}
