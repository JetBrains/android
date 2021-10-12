/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration

import com.android.tools.deployer.model.component.Complication.ComplicationType

/**
 * Includes metadata required to work with the ComplicationWatchFaceApk
 */
internal object DefaultComplicationWatchFaceInfo : ComplicationWatchFaceInfo {
  override val complicationSlots = listOf(
    ComplicationSlot(
      "Top",
      0,
      arrayOf(
        ComplicationType.SHORT_TEXT,
        ComplicationType.RANGED_VALUE,
        ComplicationType.MONOCHROMATIC_IMAGE,
        ComplicationType.SMALL_IMAGE,
        ComplicationType.LONG_TEXT
      )
    ),
    ComplicationSlot(
      "Right",
      1,
      arrayOf(
        ComplicationType.SHORT_TEXT,
        ComplicationType.RANGED_VALUE,
        ComplicationType.MONOCHROMATIC_IMAGE,
        ComplicationType.SMALL_IMAGE
      )
    ),
    ComplicationSlot(
      "Bottom",
      2,
      arrayOf(
        ComplicationType.SHORT_TEXT,
        ComplicationType.RANGED_VALUE,
        ComplicationType.MONOCHROMATIC_IMAGE,
        ComplicationType.SMALL_IMAGE,
        ComplicationType.LONG_TEXT
      )
    ),
    ComplicationSlot(
      "Left",
      3,
      arrayOf(
        ComplicationType.SHORT_TEXT,
        ComplicationType.RANGED_VALUE,
        ComplicationType.MONOCHROMATIC_IMAGE,
        ComplicationType.SMALL_IMAGE
      )
    ),
    ComplicationSlot(
      "Background",
      4,
      arrayOf(ComplicationType.PHOTO_IMAGE)
    )
  )
  override val apk = javaClass.classLoader.getResource("/apks/ComplicationWatchFace.apk")!!.path
  override val appId = "androidx.wear.watchface.samples.app"
  override val watchFaceFQName = "androidx.wear.watchface.samples.ExampleCanvasDigitalWatchFaceService"
}