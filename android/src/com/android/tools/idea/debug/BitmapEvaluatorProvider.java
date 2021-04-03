/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.debug;

import com.android.ddmlib.BitmapDecoder;
import com.google.common.collect.ImmutableList;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ByteValue;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Evaluator in the name BitmapEvaluatorProvider implies the use of the debugger evaluation mechanism to query the app for the desired
 * values.
 */
public final class BitmapEvaluatorProvider implements BitmapDecoder.BitmapDataProvider, AutoCloseable {
  /**
   * Maximum height or width of image beyond which we scale it on the device before retrieving.
   */
  private static final int MAX_DIMENSION = 1024;

  @NotNull private EvaluationContextImpl myEvaluationContext;

  @NotNull private ObjectReference myBitmap;
  private boolean isGcDisabledOnBitmap = false;

  public BitmapEvaluatorProvider(@NotNull Value bitmap, @NotNull EvaluationContextImpl evaluationContext) {
    myEvaluationContext = evaluationContext;

    // retrieve the bitmap from bitmap drawables
    String fqcn = bitmap.type().name();
    if (BitmapDecoder.BITMAP_DRAWABLE_FQCN.equals(fqcn)) {
      Value actualBitmap = getBitmapFromDrawable((ObjectReference)bitmap);
      if (actualBitmap == null) {
        throw new RuntimeException("Unable to obtain bitmap from drawable");
      }

      myBitmap = (ObjectReference)actualBitmap;
    }
    else if (!BitmapDecoder.BITMAP_FQCN.equals(fqcn)) {
      throw new RuntimeException("Invalid parameter passed into method");
    }
    else {
      myBitmap = (ObjectReference)bitmap;
    }
  }

  @Override
  @Nullable
  public String getBitmapConfigName() throws EvaluateException {
    Value config = getBitmapConfig();
    if (!(config instanceof ObjectReference)) {
      return null;
    }

    Field f = ((ObjectReference)config).referenceType().fieldByName("name");
    if (f == null) {
      return null;
    }

    return ((ObjectReference)config).getValue(f).toString();
  }

  @Override
  @Nullable
  public Dimension getDimension() throws EvaluateException {
    DebugProcessImpl debugProcess = myEvaluationContext.getDebugProcess();

    Integer w = getImageDimension(debugProcess, "getWidth");
    Integer h = getImageDimension(debugProcess, "getHeight");

    return (w != null && h != null) ? new Dimension(w, h) : null;
  }

  @Override
  public boolean downsizeBitmap(@NotNull Dimension currentDimensions) throws EvaluateException {
    DebugProcessImpl debugProcess = myEvaluationContext.getDebugProcess();
    Method createScaledBitmapMethod =
      DebuggerUtils.findMethod(myBitmap.referenceType(), "createScaledBitmap", "(Landroid/graphics/Bitmap;IIZ)Landroid/graphics/Bitmap;");
    if (createScaledBitmapMethod == null) {
      return false;
    }

    double s = Math.max(currentDimensions.getHeight(), currentDimensions.getWidth()) / MAX_DIMENSION;

    VirtualMachineProxyImpl vm = myEvaluationContext.getDebugProcess().getVirtualMachineProxy();
    Value dstWidth = DebuggerUtilsEx.createValue(vm, "int", (int)(currentDimensions.getWidth() / s));
    Value dstHeight = DebuggerUtilsEx.createValue(vm, "int", (int)(currentDimensions.getHeight() / s));
    Value filter = DebuggerUtilsEx.createValue(vm, "boolean", Boolean.FALSE);
    Value result = debugProcess.invokeMethod(
      myEvaluationContext, myBitmap, createScaledBitmapMethod, Arrays.asList(myBitmap, dstWidth, dstHeight, filter));
    if (result != null) {
      // Enable GC on old bitmap object, if it was disabled (i.e., if it was also a temporary). This allows calling
      // downsizeBitmap() multiple times.
      enableGarbageCollection();
      myBitmap = (ObjectReference)result;
      // Disable GC on the new temporary bitmap object so that it does not get collected while we need it.
      disableGarbageCollection();
    }
    return result != null;
  }

  @Nullable
  @Override
  public byte[] getPixelBytes(@NotNull Dimension size) throws Exception {
    List<Value> pixelValues;

    Field bufferField = myBitmap.referenceType().fieldByName("mBuffer");
    if (bufferField != null) {
      // if the buffer field is available, we can directly copy over the values
      Value bufferValue = myBitmap.getValue(bufferField);
      if (!(bufferValue instanceof ArrayReference)) {
        throw new RuntimeException("Image Buffer is not an array");
      }
      pixelValues = ((ArrayReference)bufferValue).getValues();
    }
    else {
      // if there is no buffer field (on older platforms that store data on native heap), then resort to creating a new buffer,
      // and invoking copyPixelsToBuffer to copy the pixel data into the newly created buffer
      pixelValues = copyToBuffer(size);
      if (pixelValues == null) {
        throw new RuntimeException("Unable to extract image data: Bitmap has no buffer field.");
      }
    }

    byte[] argb = new byte[pixelValues.size()];
    for (int i = 0; i < pixelValues.size(); i++) {
      Value pixelValue = pixelValues.get(i);
      if (pixelValue instanceof ByteValue) {
        argb[i] = ((ByteValue)pixelValue).byteValue();
      }
    }

    return argb;
  }

  @Nullable
  private Value getBitmapConfig() throws EvaluateException {
    DebugProcessImpl debugProcess = myEvaluationContext.getDebugProcess();
    Method getConfig = DebuggerUtils.findMethod(myBitmap.referenceType(), "getConfig", "()Landroid/graphics/Bitmap$Config;");
    if (getConfig == null) {
      return null;
    }
    return debugProcess.invokeMethod(myEvaluationContext, myBitmap, getConfig, Collections.emptyList());
  }

  @Nullable
  private Integer getImageDimension(@NotNull DebugProcessImpl debugProcess, @NotNull String methodName) throws EvaluateException {
    Method method = DebuggerUtils.findMethod(myBitmap.referenceType(), methodName, "()I");
    if (method != null) {
      Value widthValue = debugProcess.invokeMethod(myEvaluationContext, myBitmap, method, Collections.emptyList());
      if (widthValue instanceof IntegerValue) {
        return ((IntegerValue)widthValue).value();
      }
    }

    return null;
  }

  @Nullable
  private List<Value> copyToBuffer(@NotNull Dimension size) throws EvaluateException {
    DebugProcessImpl debugProcess = myEvaluationContext.getDebugProcess();
    VirtualMachineProxyImpl virtualMachineProxy = debugProcess.getVirtualMachineProxy();

    List<ReferenceType> classes = virtualMachineProxy.classesByName("byte[]");
    if (classes.size() != 1 || !(classes.get(0) instanceof ArrayType)) {
      return null;
    }
    ArrayType byteArrayType = (ArrayType)classes.get(0);

    classes = virtualMachineProxy.classesByName("java.nio.ByteBuffer");
    if (classes.size() != 1 || !(classes.get(0) instanceof ClassType)) {
      return null;
    }
    ClassType byteBufferType = (ClassType)classes.get(0);
    Method wrapMethod = DebuggerUtils.findMethod(byteBufferType, "wrap", "([B)Ljava/nio/ByteBuffer;");
    if (wrapMethod == null) {
      return null;
    }

    ArrayReference byteArray = byteArrayType.newInstance(size.width * size.height * 4);
    Value byteBufferRef = debugProcess.invokeMethod(myEvaluationContext, byteBufferType, wrapMethod, ImmutableList.of(byteArray));

    Method copyToBufferMethod = DebuggerUtils.findMethod(myBitmap.referenceType(), "copyPixelsToBuffer", "(Ljava/nio/Buffer;)V");
    if (copyToBufferMethod == null) {
      return null;
    }

    debugProcess.invokeMethod(myEvaluationContext, myBitmap, copyToBufferMethod, ImmutableList.of(byteBufferRef));
    return byteArray.getValues();
  }

  @Nullable
  private Value getBitmapFromDrawable(@NotNull ObjectReference bitmapDrawable) {
    try {
      DebugProcessImpl debugProcess = myEvaluationContext.getDebugProcess();
      Method getBitmapMethod = DebuggerUtils.findMethod(bitmapDrawable.referenceType(), "getBitmap", "()Landroid/graphics/Bitmap;");
      if (getBitmapMethod == null) {
        return null;
      }
      return debugProcess.invokeMethod(myEvaluationContext, bitmapDrawable, getBitmapMethod, Collections.emptyList());
    }
    catch (EvaluateException ignored) {
      return null;
    }
  }

  /**
   * Cleans up internal resources allocated in {@link #downsizeBitmap(Dimension)}.
   */
  @Override
  public void close() {
    enableGarbageCollection();
  }

  /**
   * Disables garbage collection on the myBitmap object, making sure that any subsequent method invocations with that object via JDI will
   * not throw {@link ObjectCollectedException}. This method should be invoked only on ObjectReferences to Bitmap objects created by JDI VM
   * (e.g., the Bitmap object created by {@link #downsizeBitmap(Dimension)} method). Actual objects (e.g., the original Bitmap passed into
   * the constructor) inside the target app do not require disabling garbage collection.
   */
  private void disableGarbageCollection() {
    DebuggerUtilsEx.disableCollection(myBitmap);
    isGcDisabledOnBitmap = true;
  }

  /**
   * Re-enables garbage collection on the myBitmap object, making sure that a Bitmap object created by {@link #downsizeBitmap(Dimension)}
   * does not leak.
   */
  private void enableGarbageCollection() {
    if (isGcDisabledOnBitmap) {
      DebuggerUtilsEx.enableCollection(myBitmap);
      isGcDisabledOnBitmap = false;
    }
  }
}
