/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.intellij.openapi.actionSystem.DataKey

internal enum class ZoomType {
  IN,
  OUT,
  FIT,
  ACTUAL,
}

/** Implemented by components supporting different presentation scales. */
internal interface Zoomable {
  /** The scaling level of the view. Size of the view pixel */
  val scale: Double

  /** Scale factor of the host screen. Size of a logical pixel in physical pixels. */
  val screenScalingFactor: Double

  /** Changes scale of the view. Returns true if the scale has indeed changed, otherwise false. */
  fun zoom(type: ZoomType): Boolean

  /** Checks if the given zoom operation is currently possible. */
  fun canZoom(type: ZoomType): Boolean
}

@JvmField internal val ZOOMABLE_KEY = DataKey.create<Zoomable>(Zoomable::javaClass.name)
