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
package com.android.tools.profilers.memory.adapters.instancefilters

import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.FakeInstanceObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BitmapDuplicationInstanceFilterTest {

  @Test
  fun filterSelectsOnlyDuplicateBitmaps() {
    val capture = FakeCaptureObject.Builder().build()

    // Instances that are considered duplicates
    val duplicateBitmap1 = FakeInstanceObject.Builder(capture, 1, "android.graphics.Bitmap").build()
    val duplicateBitmap2 = FakeInstanceObject.Builder(capture, 2, "android.graphics.Bitmap").build()

    // An instance that is not a duplicate
    val uniqueBitmap = FakeInstanceObject.Builder(capture, 3, "android.graphics.Bitmap").build()

    // An instance that is not a bitmap at all
    val notABitmap = FakeInstanceObject.Builder(capture, 4, "java.lang.String").build()

    // The set of all instances to be filtered
    val allInstances = setOf(duplicateBitmap1, duplicateBitmap2, uniqueBitmap, notABitmap)

    // The pre-computed set of duplicate bitmaps
    val duplicateSet = setOf(duplicateBitmap1, duplicateBitmap2)

    // Create the filter with the set of duplicates
    val filter = BitmapDuplicationInstanceFilter(duplicateSet)

    // Apply the filter
    val result = filter.filter(allInstances)

    // Verify that only the duplicate bitmaps are in the result
    assertThat(result).containsExactly(duplicateBitmap1, duplicateBitmap2)
  }

  @Test
  fun filterWithEmptyDuplicateSetReturnsEmpty() {
    val capture = FakeCaptureObject.Builder().build()

    val bitmap1 = FakeInstanceObject.Builder(capture, 1, "android.graphics.Bitmap").build()
    val bitmap2 = FakeInstanceObject.Builder(capture, 2, "android.graphics.Bitmap").build()

    val allInstances = setOf(bitmap1, bitmap2)
    val duplicateSet = emptySet<InstanceObject>()

    val filter = BitmapDuplicationInstanceFilter(duplicateSet)
    val result = filter.filter(allInstances)

    assertThat(result).isEmpty()
  }

  @Test
  fun filterWithEmptyInstancesReturnsEmpty() {
    val capture = FakeCaptureObject.Builder().build()

    val duplicateBitmap1 = FakeInstanceObject.Builder(capture, 1, "android.graphics.Bitmap").build()
    val duplicateBitmap2 = FakeInstanceObject.Builder(capture, 2, "android.graphics.Bitmap").build()

    val allInstances = emptySet<InstanceObject>()
    val duplicateSet = setOf(duplicateBitmap1, duplicateBitmap2)

    val filter = BitmapDuplicationInstanceFilter(duplicateSet)
    val result = filter.filter(allInstances)

    assertThat(result).isEmpty()
  }
}
