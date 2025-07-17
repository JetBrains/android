/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters.instancefilters;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.profilers.memory.adapters.FakeCaptureObject;
import com.android.tools.profilers.memory.adapters.FakeInstanceObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.junit.Test;

public class BitmapDuplicationInstanceFilterTest {

  @Test
  public void filterSelectsOnlyDuplicateBitmaps() {
    FakeCaptureObject capture = new FakeCaptureObject.Builder().build();

    // Instances that are considered duplicates
    FakeInstanceObject duplicateBitmap1 =
      new FakeInstanceObject.Builder(capture, 1, "android.graphics.Bitmap").build();
    FakeInstanceObject duplicateBitmap2 =
      new FakeInstanceObject.Builder(capture, 2, "android.graphics.Bitmap").build();

    // An instance that is not a duplicate
    FakeInstanceObject uniqueBitmap =
      new FakeInstanceObject.Builder(capture, 3, "android.graphics.Bitmap").build();

    // An instance that is not a bitmap at all
    FakeInstanceObject notABitmap =
      new FakeInstanceObject.Builder(capture, 4, "java.lang.String").build();

    // The set of all instances to be filtered
    Set<InstanceObject> allInstances =
      ImmutableSet.of(duplicateBitmap1, duplicateBitmap2, uniqueBitmap, notABitmap);

    // The pre-computed set of duplicate bitmaps
    Set<InstanceObject> duplicateSet = ImmutableSet.of(duplicateBitmap1, duplicateBitmap2);

    // Create the filter with the set of duplicates
    BitmapDuplicationInstanceFilter filter = new BitmapDuplicationInstanceFilter(duplicateSet);

    // Apply the filter
    Set<InstanceObject> result = filter.filter(allInstances);

    // Verify that only the duplicate bitmaps are in the result
    assertThat(result).containsExactly(duplicateBitmap1, duplicateBitmap2);
  }

  @Test
  public void filterWithEmptyDuplicateSetReturnsEmpty() {
    FakeCaptureObject capture = new FakeCaptureObject.Builder().build();

    FakeInstanceObject bitmap1 =
      new FakeInstanceObject.Builder(capture, 1, "android.graphics.Bitmap").build();
    FakeInstanceObject bitmap2 =
      new FakeInstanceObject.Builder(capture, 2, "android.graphics.Bitmap").build();

    Set<InstanceObject> allInstances = ImmutableSet.of(bitmap1, bitmap2);
    Set<InstanceObject> duplicateSet = ImmutableSet.of();

    BitmapDuplicationInstanceFilter filter = new BitmapDuplicationInstanceFilter(duplicateSet);
    Set<InstanceObject> result = filter.filter(allInstances);

    assertThat(result).isEmpty();
  }

  @Test
  public void filterWithEmptyInstancesReturnsEmpty() {
    FakeCaptureObject capture = new FakeCaptureObject.Builder().build();

    FakeInstanceObject duplicateBitmap1 =
      new FakeInstanceObject.Builder(capture, 1, "android.graphics.Bitmap").build();
    FakeInstanceObject duplicateBitmap2 =
      new FakeInstanceObject.Builder(capture, 2, "android.graphics.Bitmap").build();

    Set<InstanceObject> allInstances = ImmutableSet.of();
    Set<InstanceObject> duplicateSet = ImmutableSet.of(duplicateBitmap1, duplicateBitmap2);

    BitmapDuplicationInstanceFilter filter = new BitmapDuplicationInstanceFilter(duplicateSet);
    Set<InstanceObject> result = filter.filter(allInstances);

    assertThat(result).isEmpty();
  }
}
