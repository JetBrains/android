/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui.ui

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.WorkerThread
import com.intellij.ui.scale.ScaleContext
import java.awt.Image

interface ScaledImageProvider {
  /**
   * Returns (optionally) the initial image to be displayed
   */
  val initialImage: Image?
    @AnyThread
    get() = null

  /**
   * Returns a scaled [Image] with all bits available. See [ScalingImagePanel].
   */
  @Throws(java.io.IOException::class)
  @WorkerThread
  fun createScaledImage(ctx: ScaleContext, width: Double, height: Double): Image
}
