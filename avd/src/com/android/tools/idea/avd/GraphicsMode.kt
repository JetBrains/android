/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avd

import com.android.sdklib.ISystemImage
import com.android.sdklib.internal.avd.GpuMode

internal enum class GraphicsMode {
  AUTO,
  HARDWARE,
  SOFTWARE;

  override fun toString(): String =
    when (this) {
      AUTO -> "Automatic"
      HARDWARE -> "Hardware"
      SOFTWARE -> "Software"
    }
}

internal fun GraphicsMode.toGpuMode(systemImage: ISystemImage) =
  when (this) {
    GraphicsMode.AUTO -> GpuMode.AUTO
    GraphicsMode.HARDWARE -> GpuMode.HOST
    GraphicsMode.SOFTWARE -> GpuMode.getSoftwareGpuMode(systemImage)
  }

internal fun GpuMode.toGraphicsMode() =
  when (this) {
    GpuMode.AUTO -> GraphicsMode.AUTO
    GpuMode.HOST -> GraphicsMode.HARDWARE
    GpuMode.SWIFT,
    GpuMode.OFF -> GraphicsMode.SOFTWARE
  }
