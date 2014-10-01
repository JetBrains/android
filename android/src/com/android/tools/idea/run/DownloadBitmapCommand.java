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
package com.android.tools.idea.run;

import com.google.common.collect.ImmutableList;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.progress.util.ProgressWindowWithNotification;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DownloadBitmapCommand extends SuspendContextCommandImpl {
  private static final String BITMAP_FQCN = "android.graphics.Bitmap";
  private static final String BITMAP_DRAWABLE_FQCN = "android.graphics.drawable.BitmapDrawable";

  /** Maximum height or width of image beyond which we scale it on the device before retrieving. */
  private static final int MAX_DIMENSION = 1024;

  private final Value myBitmapValue;
  private final DebuggerContextImpl myDebuggerContext;
  private final ProgressWindowWithNotification myProgressWindow;
  private final CompletionCallback myCallback;

  public static boolean isSupportedBitmap(Value value) {
    String fqcn = value.type().name();
    return BITMAP_FQCN.equals(fqcn) || BITMAP_DRAWABLE_FQCN.equals(fqcn);
  }

  public interface CompletionCallback {
    void bitmapDownloaded(@NotNull BufferedImage image);
    void error(@NotNull String message);
  }

  public DownloadBitmapCommand(@NotNull Value bitmapValue,
                               @NotNull DebuggerContextImpl debuggerContext,
                               @NotNull CompletionCallback callback,
                               @NotNull ProgressWindowWithNotification progressWindow) {
    super(debuggerContext.getSuspendContext());
    myBitmapValue = bitmapValue;
    myDebuggerContext = debuggerContext;
    myCallback = callback;
    myProgressWindow = progressWindow;
  }

  @Override
  public Priority getPriority() {
    return Priority.HIGH;
  }

  @Override
  public void contextAction() throws Exception {
    myProgressWindow.setText("Examining bitmap");
    Value bitmap = myBitmapValue;
    EvaluationContextImpl evaluationContext = myDebuggerContext.createEvaluationContext();

    // retrieve the bitmap from bitmap drawables
    String fqcn = bitmap.type().name();
    if (BITMAP_DRAWABLE_FQCN.equals(fqcn)) {
      bitmap = getBitmapFromDrawable(evaluationContext, (ObjectReference)bitmap);
      if (bitmap == null) {
        myCallback.error("Unable to obtain bitmap from drawable");
        return;
      }
    }

    String config = getBitmapConfigName((ObjectReference)bitmap, evaluationContext);
    if (!"\"ARGB_8888\"".equals(config)) {
      myCallback.error("Unsupported bitmap configuration: " + config);
      return;
    }

    Dimension size = getDimension(evaluationContext, bitmap);
    if (size == null) {
      myCallback.error("Unable to determine image dimensions.");
      return;
    }

    // if the image is rather large, then scale it down
    if (size.width > MAX_DIMENSION || size.height > MAX_DIMENSION) {
      myProgressWindow.setText("Scaling down bitmap");
      bitmap = createScaledBitmap(evaluationContext, (ObjectReference)bitmap, size);
      if (bitmap == null) {
        myCallback.error("Unable to create scaled bitmap");
        return;
      }

      size = getDimension(evaluationContext, bitmap);
      if (size == null) {
        myCallback.error("Unable to obtained scaled bitmap's dimensions");
        return;
      }
    }

    if (myProgressWindow.isCanceled()) {
      return;
    }
    myProgressWindow.setText("Retrieving pixel data");

    List<Value> pixelValues;

    Field bufferField = ((ObjectReference)bitmap).referenceType().fieldByName("mBuffer");
    if (bufferField != null) {
      // if the buffer field is available, we can directly copy over the values
      Value bufferValue = ((ObjectReference)bitmap).getValue(bufferField);
      if (!(bufferValue instanceof ArrayReference)) {
        return;
      }
      pixelValues =((ArrayReference)bufferValue).getValues();
    } else {
      // if there is no buffer field (on older platforms that store data on native heap), then resort to creating a new buffer,
      // and invoking copyPixelsToBuffer to copy the pixel data into the newly created buffer
      pixelValues = copyToBuffer(evaluationContext, (ObjectReference)bitmap, size);
      if (pixelValues == null) {
        myCallback.error("Unable to extract image data: Bitmap has no buffer field.");
        return;
      }
    }

    byte[] argb = new byte[pixelValues.size()];
    for (int i = 0; i < pixelValues.size(); i++) {
      Value pixelValue = pixelValues.get(i);
      if (pixelValue instanceof ByteValue) {
        argb[i] = ((ByteValue)pixelValue).byteValue();
      }
    }

    if (myProgressWindow.isCanceled()) {
      return;
    }

    myCallback.bitmapDownloaded(createBufferedImage(size.width, size.height, argb));
  }

  @Nullable
  private static String getBitmapConfigName(ObjectReference bitmap, EvaluationContextImpl evaluationContext) throws EvaluateException {
    Value config = getBitmapConfig(evaluationContext, bitmap);
    if (!(config instanceof ObjectReference)) {
      return null;
    }

    Field f = ((ObjectReference)config).referenceType().fieldByName("name");
    if (f == null) {
      return null;
    }

    return ((ObjectReference)config).getValue(f).toString();
  }

  @Nullable
  private static List<Value> copyToBuffer(EvaluationContextImpl evaluationContext, ObjectReference bitmap, Dimension size) throws EvaluateException {
    DebugProcessImpl debugProcess = evaluationContext.getDebugProcess();
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
    Value byteBufferRef = debugProcess.invokeMethod(evaluationContext, byteBufferType, wrapMethod, ImmutableList.of(byteArray));

    Method copyToBufferMethod = DebuggerUtils.findMethod(bitmap.referenceType(), "copyPixelsToBuffer", "(Ljava/nio/Buffer;)V");
    if (copyToBufferMethod == null) {
      return null;
    }

    debugProcess.invokeMethod(evaluationContext, bitmap, copyToBufferMethod, ImmutableList.of(byteBufferRef));
    return byteArray.getValues();
  }

  @Nullable
  private static Dimension getDimension(@NotNull EvaluationContextImpl context, @NotNull Value bitmap) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();

    Integer w = getImageDimension(context, (ObjectReference)bitmap, debugProcess, "getWidth");
    Integer h = getImageDimension(context, (ObjectReference)bitmap, debugProcess, "getHeight");

    return (w != null & h != null) ? new Dimension(w, h) : null;
  }

  @Nullable
  private static Value getBitmapConfig(EvaluationContextImpl context, ObjectReference bitmap) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Method getConfig = DebuggerUtils.findMethod(bitmap.referenceType(), "getConfig", "()Landroid/graphics/Bitmap$Config;");
    if (getConfig == null) {
      return null;
    }
    return debugProcess.invokeMethod(context, bitmap, getConfig, Collections.emptyList());
  }

  @Nullable
  private static Value getBitmapFromDrawable(@NotNull EvaluationContextImpl context, @NotNull ObjectReference bitmap)
    throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Method getBitmapMethod = DebuggerUtils
      .findMethod(bitmap.referenceType(), "getBitmap", "()Landroid/graphics/Bitmap;");
    if (getBitmapMethod == null) {
      return null;
    }
    return debugProcess.invokeMethod(context, bitmap, getBitmapMethod, Collections.emptyList());
  }

  @Nullable
  private static Value createScaledBitmap(@NotNull EvaluationContextImpl context,
                                          @NotNull ObjectReference bitmap,
                                          @NotNull Dimension currentDimensions) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Method createScaledBitmapMethod = DebuggerUtils
      .findMethod(bitmap.referenceType(), "createScaledBitmap", "(Landroid/graphics/Bitmap;IIZ)Landroid/graphics/Bitmap;");
    if (createScaledBitmapMethod == null) {
      return null;
    }

    double s = Math.max(currentDimensions.getHeight(), currentDimensions.getWidth()) / MAX_DIMENSION;

    VirtualMachineProxyImpl vm = context.getDebugProcess().getVirtualMachineProxy();
    Value dstWidth = DebuggerUtilsEx.createValue(vm, "int", (int)(currentDimensions.getWidth() / s));
    Value dstHeight = DebuggerUtilsEx.createValue(vm, "int", (int)(currentDimensions.getHeight() / s));
    Value filter = DebuggerUtilsEx.createValue(vm, "boolean", Boolean.FALSE);
    return debugProcess.invokeMethod(context, bitmap, createScaledBitmapMethod,
                                     Arrays.asList(bitmap, dstWidth, dstHeight, filter));
  }

  @Nullable
  private static Integer getImageDimension(EvaluationContextImpl context,
                                           ObjectReference bitmap,
                                           DebugProcessImpl debugProcess,
                                           String methodName) throws EvaluateException {
    Method method = DebuggerUtils.findMethod((bitmap).referenceType(), methodName, "()I");
    if (method != null) {
      Value widthValue = debugProcess.invokeMethod(context, bitmap, method, Collections.emptyList());
      if (widthValue instanceof IntegerValue) {
        return ((IntegerValue)widthValue).value();
      }
    }

    return null;
  }

  private static BufferedImage createBufferedImage(int width, int height, byte[] rgba) throws IOException {
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int i = (y * width + x) * 4;
        long rgb = 0;
        rgb |= ((long)rgba[i+0] & 0xff) << 16; // r
        rgb |= ((long)rgba[i+1] & 0xff) << 8;  // g
        rgb |= ((long)rgba[i+2] & 0xff) << 0;  // b
        rgb |= ((long)rgba[i+3] & 0xff) << 24; // a
        bufferedImage.setRGB(x, y, (int)(rgb & 0xffffffff));
      }
    }

    return bufferedImage;
  }
}