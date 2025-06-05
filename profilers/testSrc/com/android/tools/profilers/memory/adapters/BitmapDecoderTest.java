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

import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.ARRAY;
import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.BOOLEAN;
import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.BYTE;
import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.INT;
import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.LONG;
import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.OBJECT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class BitmapDecoderTest {
  final String BITMAP_CLASS_NAME = "android.graphics.Bitmap";
  final String BYTES_CLASS_NAME = "byte[]";

  @Test
  public void dataProviderTestWithMBuffer() {
    FakeCaptureObject fakeCaptureObject = new FakeCaptureObject.Builder().build();

    FakeInstanceObject bitmapInstance = new FakeInstanceObject.Builder(fakeCaptureObject, 1, BITMAP_CLASS_NAME)
      .setFields(Arrays.asList("mBuffer", "mIsMutable", "mWidth", "mHeight")).build();
    bitmapInstance
      .setFieldValue("mBuffer", ARRAY,
                     new FakeInstanceObject.Builder(fakeCaptureObject, 2, BYTES_CLASS_NAME).setValueType(ARRAY)
                       .setArray(BYTE, new byte[]{0, 0, 0, 0, 0, 0, 0, 0,}, 8).build())
      .setFieldValue("mWidth", INT, 2)
      .setFieldValue("mHeight", INT, 1)
      .setFieldValue("mIsMutable", BOOLEAN, false);

    FakeInstanceObject badBitmapInstance1 = new FakeInstanceObject.Builder(fakeCaptureObject, 1, BITMAP_CLASS_NAME)
      .setFields(Arrays.asList("mBuffer", "mIsMutable", "mWidth", "mHeight")).build();
    bitmapInstance // dimension larger than buffer
      .setFieldValue("mBuffer", ARRAY,
                     new FakeInstanceObject.Builder(fakeCaptureObject, 2, BYTES_CLASS_NAME).setValueType(ARRAY)
                       .setArray(BYTE, new byte[]{0, 0, 0, 0, 0, 0, 0, 0,}, 8).build())
      .setFieldValue("mWidth", INT, 3)
      .setFieldValue("mHeight", INT, 1)
      .setFieldValue("mIsMutable", BOOLEAN, false);

    FakeInstanceObject badBitmapInstance2 = new FakeInstanceObject.Builder(fakeCaptureObject, 1, BITMAP_CLASS_NAME)
      .setFields(Arrays.asList("mBuffer", "mIsMutable", "mWidth", "mHeight")).build();
    bitmapInstance // MIA fields
      .setFieldValue("mBuffer", ARRAY,
                     new FakeInstanceObject.Builder(fakeCaptureObject, 2, BYTES_CLASS_NAME).setValueType(ARRAY)
                       .setArray(BYTE, new byte[]{0, 0, 0, 0, 0, 0, 0, 0,}, 8).build())
      .setFieldValue("mWidth", INT, 2)
      .setFieldValue("mIsMutable", BOOLEAN, false);

    fakeCaptureObject.addInstanceObjects(ImmutableSet.of(bitmapInstance, badBitmapInstance1, badBitmapInstance2));

    BitmapDecoder.BitmapDataProvider dataProvider = AndroidBitmapDataProvider.createDecoder(bitmapInstance);
    assertNotNull(dataProvider);
    assertNotNull(BitmapDecoder.getBitmap(dataProvider));
    assertNull(AndroidBitmapDataProvider.createDecoder(badBitmapInstance1));
    assertNull(AndroidBitmapDataProvider.createDecoder(badBitmapInstance2));
  }

  @Test
  public void dataProviderTestWithShadowKlassCorrectInstance() {
    FakeCaptureObject fakeCaptureObject = new FakeCaptureObject.Builder().build();

    FakeInstanceObject buffers = new FakeInstanceObject.Builder(fakeCaptureObject, 6, "byte[][]").setFields(List.of("0")).build();
    buffers.setFieldValue("0", ARRAY, new FakeInstanceObject.Builder(fakeCaptureObject, 5, BYTES_CLASS_NAME).setValueType(ARRAY)
      .setArray(BYTE,
                new byte[]{-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 2, 0, 0, 0, 1, 8, 6, 0, 0, 0, -12, 34,
                  127, -118, 0, 0, 0, 14, 73, 68, 65, 84, 120, 94, 99, -8, -49, -64, 0, 66, -1, 1, 15, -7, 3, -3, -8, -86, -104, -127, 0, 0,
                  0, 0, 73, 69, 78, 68, -82, 66, 96, -126}, 71).build());

    FakeInstanceObject natives = new FakeInstanceObject.Builder(fakeCaptureObject, 4, BYTES_CLASS_NAME)
      .setFields(List.of("0")).build();
    natives
      .setFieldValue("0", LONG, -1L);

    FakeInstanceObject dumpDataInstance = new FakeInstanceObject.Builder(fakeCaptureObject, 3, "Bitmap$DumpData")
      .setFields(Arrays.asList("buffers", "natives")).build();
    dumpDataInstance
      .setFieldValue("buffers", OBJECT, buffers).setFieldValue("natives", OBJECT, natives);

    FakeInstanceObject shadowKlassInstance = new FakeInstanceObject.Builder(fakeCaptureObject, 2, "Class")
      .setFields(List.of("dumpData")).build();
    shadowKlassInstance
      .setFieldValue("dumpData", OBJECT, dumpDataInstance);

    FakeInstanceObject shadowBufferBitmapInstance = new FakeInstanceObject.Builder(fakeCaptureObject, 1, BITMAP_CLASS_NAME)
      .setFields(Arrays.asList("shadow$_klass_", "mWidth", "mHeight", "mNativePtr")).build();
    shadowBufferBitmapInstance
      .setFieldValue("shadow$_klass_", OBJECT, shadowKlassInstance)
      .setFieldValue("mWidth", INT, 2)
      .setFieldValue("mHeight", INT, 1)
      .setFieldValue("mNativePtr", LONG, -1L);

    fakeCaptureObject.addInstanceObjects(
      ImmutableSet.of(shadowBufferBitmapInstance));

    BitmapDecoder.BitmapDataProvider dataProvider = AndroidBitmapDataProvider.createDecoder(shadowBufferBitmapInstance);
    assertNotNull(dataProvider);
    assertNotNull(BitmapDecoder.getBitmap(dataProvider));
  }

  @Test
  public void dataProviderTestWithShadowKlassIncorrectInstance() {
    FakeCaptureObject fakeCaptureObject = new FakeCaptureObject.Builder().build();

    FakeInstanceObject buffers1 = new FakeInstanceObject.Builder(fakeCaptureObject, 6, "byte[][]").setFields(List.of("0")).build();
    buffers1.setFieldValue("0", ARRAY, new FakeInstanceObject.Builder(fakeCaptureObject, 5, BYTES_CLASS_NAME).setValueType(ARRAY)
      .setArray(BYTE, new byte[]{0, 0, 0, 0, 0, 0}, 6).build());

    FakeInstanceObject natives = new FakeInstanceObject.Builder(fakeCaptureObject, 4, BYTES_CLASS_NAME)
      .setFields(List.of("0")).build();
    natives
      .setFieldValue("0", LONG, -1L);

    FakeInstanceObject dumpDataInstance = new FakeInstanceObject.Builder(fakeCaptureObject, 3, "Bitmap$DumpData")
      .setFields(Arrays.asList("buffers", "natives")).build();
    dumpDataInstance
      .setFieldValue("buffers", OBJECT, buffers1).setFieldValue("natives", OBJECT, natives);

    FakeInstanceObject shadowKlassInstance = new FakeInstanceObject.Builder(fakeCaptureObject, 2, "Class")
      .setFields(List.of("dumpData")).build();
    shadowKlassInstance
      .setFieldValue("dumpData", OBJECT, dumpDataInstance);

    FakeInstanceObject shadowBufferBitmapInstance = new FakeInstanceObject.Builder(fakeCaptureObject, 1, BITMAP_CLASS_NAME)
      .setFields(Arrays.asList("shadow$_klass_", "mWidth", "mHeight", "mNativePtr")).build();
    shadowBufferBitmapInstance
      .setFieldValue("shadow$_klass_", OBJECT, shadowKlassInstance)
      .setFieldValue("mWidth", INT, 2)
      .setFieldValue("mHeight", INT, 1)
      .setFieldValue("mNativePtr", LONG, -1L);

    fakeCaptureObject.addInstanceObjects(
      ImmutableSet.of(shadowBufferBitmapInstance));

    BitmapDecoder.BitmapDataProvider dataProvider = AndroidBitmapDataProvider.createDecoder(shadowBufferBitmapInstance);
    assertNull(dataProvider);
  }
}
