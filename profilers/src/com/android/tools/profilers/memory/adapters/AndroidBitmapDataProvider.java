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

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.diagnostic.Logger;
import java.util.List;

/**
 * Adapter for Android Bitmap and BitmapDrawable objects in access the pixel data underneath.
 */
public final class AndroidBitmapDataProvider implements BitmapDecoder.BitmapDataProvider {
  private static final Logger LOG = Logger.getInstance(AndroidBitmapDataProvider.class);

  public static final String BITMAP_FQCN = "android.graphics.Bitmap";

  public static final String BITMAP_DRAWABLE_FQCN = "android.graphics.drawable.BitmapDrawable";

  private byte[] myBuffer = null;

  // Aims to capture the value of `Bitmap.mIsMutable` field if it exists. This field was removed by commit
  // e7b5129c1e7ab73b854a2f260a2e77e7d965b4cb in 2018. Therefore, a default value of `false` is used when the field isn't present.
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
    boolean isMutable = false;
    byte[] dataBuffer = null;
    Long nativePtr = null;
    InstanceObject shadowKlassInstance = null;

    for (FieldObject field : instance.getFields()) {
      String fieldName = field.getFieldName();
      Object fieldValue = field.getValue();

      if ("shadow$_klass_".equals(fieldName) && fieldValue instanceof InstanceObject) {
        shadowKlassInstance = field.getAsInstance();
      }
      else if ("mBuffer".equals(fieldName)) {
        dataBuffer = getDataBufferFromMBufferField(field);
      }
      else if ("mIsMutable".equals(fieldName) && (fieldValue instanceof Boolean)) {
        isMutable = (Boolean)fieldValue;
      }
      else if ("mWidth".equals(fieldName) && (fieldValue instanceof Integer)) {
        width = (Integer)fieldValue;
      }
      else if ("mHeight".equals(fieldName) && (fieldValue instanceof Integer)) {
        height = (Integer)fieldValue;
      }
      else if ("mNativePtr".equals(fieldName) && (fieldValue instanceof Long)) {
        nativePtr = (Long)fieldValue;
      }
    }

    // If mBuffer didn't yield data, try shadowKlass
    if (dataBuffer == null && shadowKlassInstance != null && nativePtr != null) {
      dataBuffer = getDataBufferFromShadowKlass(shadowKlassInstance, nativePtr);
    }

    if (dataBuffer == null || width == null || height == null) {
      return null;
    }

    return new AndroidBitmapDataProvider(dataBuffer, isMutable, width, height);
  }

  private static byte[] getDataBufferFromMBufferField(@NotNull FieldObject mBufferField) {
    InstanceObject bufferInstance = mBufferField.getAsInstance();
    if (bufferInstance == null) {
      return null;
    }

    ArrayObject arrayObject = bufferInstance.getArrayObject();
    if (arrayObject == null || arrayObject.getArrayElementType() != ValueObject.ValueType.BYTE) {
      return null;
    }

    byte[] buffer = arrayObject.getAsByteArray();
    if (buffer != null && buffer.length > 0) {
      return buffer;
    }

    return null;
  }

  private static byte[] getDataBufferFromShadowKlass(@NotNull InstanceObject shadowKlassInstance, @NotNull Long nativePtr) {
    byte[] imageBuffer = getDumpDataFromShadowKlass(shadowKlassInstance, nativePtr);
    if (imageBuffer == null) {
      return null;
    }

    byte[] decompressedPixelData = decompressImage(imageBuffer);

    if (decompressedPixelData != null && decompressedPixelData.length > 0) {
      return decompressedPixelData;
    }

    LOG.debug("Decoded pixel data is null or empty after image decoding. dataBuffer not updated for native pointer: " + nativePtr);
    return null;
  }

  private static byte[] getDumpDataFromShadowKlass(@NotNull InstanceObject shadowKlassInstance, @NotNull Long nativePtr) {
    InstanceObject dumpDataInstance = getNestedInstanceObject(shadowKlassInstance, "dumpData");

    if (dumpDataInstance == null) {
      return null;
    }

    return processBuffersFromDumpData(dumpDataInstance, nativePtr);
  }

  private static byte[] processBuffersFromDumpData(@NotNull InstanceObject dumpDataInstance, @NotNull Long nativePtr) {
    InstanceObject buffersInstance = getNestedInstanceObject(dumpDataInstance, "buffers");
    InstanceObject nativesInstance = getNestedInstanceObject(dumpDataInstance, "natives");

    if (buffersInstance == null || nativesInstance == null) {
      LOG.debug("Required 'buffers' or 'natives' fields not found or are null in dumpDataInstance.");
      return null;
    }

    List<FieldObject> buffersFields = buffersInstance.getFields();
    List<FieldObject> nativesFields = nativesInstance.getFields();
    if (buffersFields.size() != nativesFields.size()) {
      LOG.warn(
        String.format(Locale.US, "Mismatch in size between 'buffers' (%d) and 'natives' (%d) fields. Cannot process.",
                      buffersFields.size(), nativesFields.size()));
      return null;
    }

    InstanceObject matchingBufferInstance = null;

    for (int i = 0; i < nativesFields.size(); i++) {
      FieldObject nativeField = nativesFields.get(i);
      if (nativeField == null) continue;

      Object nativeValue = nativeField.getValue();
      if (nativeValue instanceof Long && Objects.equals(nativePtr, nativeValue)) {
        FieldObject bufferField = buffersFields.get(i);

        if (bufferField != null) {
          matchingBufferInstance = bufferField.getAsInstance();
          break;
        }
      }
    }

    if (matchingBufferInstance == null) {
      LOG.warn("No matching buffer found for native pointer: " + nativePtr);
      return null;
    }

    ArrayObject arrayObject = matchingBufferInstance.getArrayObject();

    if (arrayObject == null) {
      LOG.warn("Buffer instance does not contain an ArrayObject.");
      return null;
    }

    if (arrayObject.getArrayElementType() != ValueObject.ValueType.BYTE) {
      LOG.warn("ArrayObject element type is not BYTE. Found: " + arrayObject.getArrayElementType());
      return null;
    }

    return arrayObject.getAsByteArray();
  }

  public static byte[] decompressImage(byte[] compressedBytes) {
    if (compressedBytes == null) {
      LOG.debug("Input image bytes are null.");
      return null;
    }

    BufferedImage bufferedImage;
    try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedBytes)) {
      bufferedImage = ImageIO.read(bais);
    }
    catch (IOException e) {
      LOG.warn("IOException during image decompression: " + e.getMessage(), e);
      return null;
    }

    if (bufferedImage == null) {
      LOG.debug("Failed to read BufferedImage from PNG bytes. ImageIO.read returned null. This might indicate invalid PNG data.");
      return null;
    }

    java.awt.image.DataBuffer buffer = bufferedImage.getRaster().getDataBuffer();

    if (buffer instanceof DataBufferByte byteBuffer) {
      return byteBuffer.getData();
    }

    LOG.warn("BufferedImage data buffer is not of type DataBufferByte. Found: " + buffer.getClass().getName());
    return null;
  }

  private static InstanceObject getNestedInstanceObject(@NotNull InstanceObject parentInstance, @NotNull String fieldName) {
    return parentInstance.getFields().stream()
      .filter(field -> fieldName.equals(field.getFieldName()) && field.getValue() instanceof InstanceObject)
      .map(FieldObject::getAsInstance) // FieldObject::getAsInstance can return null
      .filter(Objects::nonNull)       // Ensure we only consider non-null instances
      .findFirst().orElse(null);
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

    if ((!myIsMutable && ((myBuffer.length % area) != 0)) || (myIsMutable && area > myBuffer.length)) {
      return null;
    }

    return switch (pixelSize) {
      case 4 -> BitmapDecoder.PixelFormat.ARGB_8888;
      case 3 -> BitmapDecoder.PixelFormat.BGR_888;
      case 2 -> BitmapDecoder.PixelFormat.RGB_565;
      default -> BitmapDecoder.PixelFormat.ALPHA_8;
    };
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
  public static InstanceObject getBitmapClassInstance(@NotNull InstanceObject instance) {
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
