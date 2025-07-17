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

import com.android.tools.profilers.memory.adapters.InstanceObject

/**
 * A filter that shows only Bitmap instances that have been identified as duplicates
 * based on their pixel data content.
 *
 * @param duplicateBitmapSet The set of all InstanceObjects that have been pre-calculated
 *                           to be part of a duplication group by [BitmapDuplicationAnalyzer].
 */
class BitmapDuplicationInstanceFilter(
  private val duplicateBitmapSet: Set<InstanceObject>
) : CaptureObjectInstanceFilter(
  "duplicate bitmaps",
  "Show duplicate Bitmap instances",
  "Shows all Bitmap instances that have the same dimensions and pixel content as at least one other Bitmap instance.",
  null,
  // The core filter logic: an instance passes the filter if it's in the pre-computed set of duplicates.
  { instanceObject -> duplicateBitmapSet.contains(instanceObject) }
)