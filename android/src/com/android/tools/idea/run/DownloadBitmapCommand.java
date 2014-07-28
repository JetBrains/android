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
        return;
      }
    }

    Dimension size = getDimension(evaluationContext, bitmap);
    if (size == null) {
      return;
    }

    // if the image is rather large, then scale it down
    if (size.width > MAX_DIMENSION || size.height > MAX_DIMENSION) {
      myProgressWindow.setText("Scaling down bitmap");
      bitmap = createScaledBitmap(evaluationContext, (ObjectReference)bitmap, size);
      if (bitmap == null) {
        return;
      }

      size = getDimension(evaluationContext, bitmap);
      if (size == null) {
        return;
      }
    }

    if (myProgressWindow.isCanceled()) {
      return;
    }
    myProgressWindow.setText("Retrieving pixel data");

    // TODO: The mBuffer field is supposedly present in current implementations of the bitmap class,
    // but is not guaranteed to exist in the future. If not available, we should probably fall back
    // to invoking a copyPixelsToBuffer and obtaining data from that buffer
    Field bufferField = ((ObjectReference)bitmap).referenceType().fieldByName("mBuffer");
    if (bufferField == null) {
      return;
    }

    Value bufferValue = ((ObjectReference)bitmap).getValue(bufferField);
    if (!(bufferValue instanceof ArrayReference)) {
      return;
    }

    List<Value> pixelValues = ((ArrayReference)bufferValue).getValues();
    byte[] argb = new byte[pixelValues.size()]; // TODO: assumes CONFIG_ARGB
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
  private static Dimension getDimension(@NotNull EvaluationContextImpl context, @NotNull Value bitmap) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();

    Integer w = getImageDimension(context, (ObjectReference)bitmap, debugProcess, "getWidth");
    Integer h = getImageDimension(context, (ObjectReference)bitmap, debugProcess, "getHeight");

    return (w != null & h != null) ? new Dimension(w, h) : null;
  }

  @Nullable
  private static Value getBitmapFromDrawable(@NotNull EvaluationContextImpl context, @NotNull ObjectReference bitmap)
    throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Method getBitmapMethod = DebuggerUtils
      .findMethod((bitmap).referenceType(), "getBitmap", "()Landroid/graphics/Bitmap;");
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
      .findMethod((bitmap).referenceType(), "createScaledBitmap", "(Landroid/graphics/Bitmap;IIZ)Landroid/graphics/Bitmap;");
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