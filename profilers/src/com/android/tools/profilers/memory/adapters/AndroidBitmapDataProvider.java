/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Adapater for Android Bitmap and BitmapDrawable objects in access the pixel data underneath.
 */
public final class AndroidBitmapDataProvider implements BitmapDecoder.BitmapDataProvider {
  public static final String BITMAP_FQCN = "android.graphics.Bitmap";

  public static final String BITMAP_DRAWABLE_FQCN = "android.graphics.drawable.BitmapDrawable";

  private byte[] myBuffer = null;

  private boolean myIsMutable = false;

  private int myWidth = -1;

  private int myHeight = -1;

  @Nullable
  public static AndroidBitmapDataProvider createDecoder(@NotNull InstanceObject instance) {
    instance = getBitmapClassInstance(instance);
    if (instance == null) {
      return null;
    }

    Integer width = null;
    Integer height = null;
    Boolean isMutable = null;
    byte[] dataBuffer = null;

    for (FieldObject field : instance.getFields()) {
      Object fieldValue = field.getValue();
      if ("mBuffer".equals(field.getFieldName())) {
        InstanceObject bufferInstance = field.getAsInstance();
        if (bufferInstance == null) {
          continue;
        }

        ArrayObject arrayObject = bufferInstance.getArrayObject();
        if (arrayObject == null || arrayObject.getArrayElementType() != ValueObject.ValueType.BYTE) {
          continue;
        }

        dataBuffer = arrayObject.getAsByteArray();
      }
      else if ("mIsMutable".equals(field.getFieldName()) && (fieldValue instanceof Boolean)) {
        isMutable = (Boolean)fieldValue;
      }
      else if ("mWidth".equals(field.getFieldName()) && (fieldValue instanceof Integer)) {
        width = (Integer)fieldValue;
      }
      else if ("mHeight".equals(field.getFieldName()) && (fieldValue instanceof Integer)) {
        height = (Integer)fieldValue;
      }
    }

    if (dataBuffer == null ||
        isMutable == null ||
        width == null ||
        height == null) {
      return null;
    }

    return new AndroidBitmapDataProvider(dataBuffer, isMutable, width, height);
  }

  private AndroidBitmapDataProvider(@NotNull byte[] buffer, boolean isMutable, int width, int height) {
    myBuffer = buffer;
    myIsMutable = isMutable;
    myWidth = width;
    myHeight = height;
  }

  @Nullable
  @Override
  public BitmapDecoder.PixelFormat getBitmapConfigName() {
    int area = myWidth * myHeight;
    int pixelSize = myBuffer.length / area;

    if ((!myIsMutable && ((myBuffer.length % area) != 0)) ||
        (myIsMutable && area > myBuffer.length)) {
      return null;
    }

    switch (pixelSize) {
      case 4:
        return BitmapDecoder.PixelFormat.ARGB_8888;
      case 2:
        return BitmapDecoder.PixelFormat.RGB_565;
      default:
        return BitmapDecoder.PixelFormat.ALPHA_8;
    }
  }

  @Nullable
  @Override
  public Dimension getDimension() {
    return myWidth < 0 || myHeight < 0 ? null : new Dimension(myWidth, myHeight);
  }

  @Nullable
  @Override
  public byte[] getPixelBytes(@NotNull Dimension size) {
    return myBuffer;
  }

  @Nullable
  private static InstanceObject getBitmapClassInstance(@NotNull InstanceObject instance) {
    String className = instance.getClassEntry().getClassName();
    if (BITMAP_FQCN.equals(className)) {
      return instance;
    }
    else if (BITMAP_DRAWABLE_FQCN.equals(className)) {
      return getBitmapFromDrawable(instance);
    }

    return null;
  }

  @Nullable
  private static InstanceObject getBitmapFromDrawable(@NotNull InstanceObject instance) {
    InstanceObject bitmapState = getBitmapStateFromBitmapDrawable(instance);
    if (bitmapState == null) {
      return null;
    }

    for (FieldObject field : bitmapState.getFields()) {
      if ("mBitmap".equals(field.getFieldName())) {
        InstanceObject value = field.getAsInstance();
        if (value == null) {
          continue;
        }

        String className = value.getClassEntry().getClassName();
        if (BITMAP_FQCN.equals(className)) {
          return value;
        }
      }
    }
    return null;
  }

  @Nullable
  private static InstanceObject getBitmapStateFromBitmapDrawable(@NotNull InstanceObject bitmapDrawable) {
    for (FieldObject field : bitmapDrawable.getFields()) {
      if ("mBitmapState".equals(field.getFieldName())) {
        InstanceObject value = field.getAsInstance();
        if (value == null) {
          continue;
        }

        String className = value.getClassEntry().getClassName();
        if ((BITMAP_DRAWABLE_FQCN + "$BitmapState").equals(className)) {
          return value;
        }
      }
    }
    return null;
  }
}
