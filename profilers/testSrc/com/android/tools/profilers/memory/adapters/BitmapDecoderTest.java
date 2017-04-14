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

import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.util.Arrays;

import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class BitmapDecoderTest {
  @Test
  public void dataProviderTest() {
    final String BITMAP_CLASS_NAME = "android.graphics.Bitmap";

    FakeCaptureObject fakeCaptureObject = new FakeCaptureObject.Builder().build();

    FakeInstanceObject bitmapInstance = new FakeInstanceObject.Builder(fakeCaptureObject, BITMAP_CLASS_NAME)
      .setFields(Arrays.asList("mBuffer", "mIsMutable", "mWidth", "mHeight")).build();
    bitmapInstance
      .setFieldValue("mBuffer", ARRAY, new FakeInstanceObject.Builder(fakeCaptureObject, "byte[]").setValueType(ARRAY)
        .setArray(BYTE, new byte[]{0, 0, 0, 0, 0, 0, 0, 0,}, 8).build())
      .setFieldValue("mWidth", INT, 2)
      .setFieldValue("mHeight", INT, 1)
      .setFieldValue("mIsMutable", BOOLEAN, false);

    FakeInstanceObject badBitmapInstance1 = new FakeInstanceObject.Builder(fakeCaptureObject, BITMAP_CLASS_NAME)
      .setFields(Arrays.asList("mBuffer", "mIsMutable", "mWidth", "mHeight")).build();
    bitmapInstance // dimension larger than buffer
      .setFieldValue("mBuffer", ARRAY, new FakeInstanceObject.Builder(fakeCaptureObject, "byte[]").setValueType(ARRAY)
        .setArray(BYTE, new byte[]{0, 0, 0, 0, 0, 0, 0, 0,}, 8).build())
      .setFieldValue("mWidth", INT, 3)
      .setFieldValue("mHeight", INT, 1)
      .setFieldValue("mIsMutable", BOOLEAN, false);

    FakeInstanceObject badBitmapInstance2 = new FakeInstanceObject.Builder(fakeCaptureObject, BITMAP_CLASS_NAME)
      .setFields(Arrays.asList("mBuffer", "mIsMutable", "mWidth", "mHeight")).build();
    bitmapInstance // MIA fields
      .setFieldValue("mBuffer", ARRAY, new FakeInstanceObject.Builder(fakeCaptureObject, "byte[]").setValueType(ARRAY)
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
}
