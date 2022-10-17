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
import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Returns the base directory for the Sample Data directory contents
 */
private fun getApksBaseDir(): Path? {
  val homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath())
  var apksPath = Paths.get(homePath, "plugins/android/resources/apks")
  if (StudioPathManager.isRunningFromSources()) {
    apksPath = StudioPathManager.resolvePathFromSourcesRoot("tools/adt/idea/android/lib/apks")
  }
  if (Files.exists(apksPath)) {
    return apksPath
  }

  Logger.getInstance(DefaultComplicationWatchFaceInfo::class.java).error("Unable to find apks dir at $apksPath")
  return null
}

/**
 * Includes metadata required to work with the ComplicationWatchFaceApk
 */
object DefaultComplicationWatchFaceInfo : ComplicationWatchFaceInfo {
  override val complicationSlots = listOf(
    ComplicationSlot(
      "Top",
      0,
      arrayOf(
        ComplicationType.SHORT_TEXT,
        ComplicationType.RANGED_VALUE,
        ComplicationType.ICON,
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
        ComplicationType.ICON,
        ComplicationType.SMALL_IMAGE
      )
    ),
    ComplicationSlot(
      "Bottom",
      2,
      arrayOf(
        ComplicationType.SHORT_TEXT,
        ComplicationType.RANGED_VALUE,
        ComplicationType.ICON,
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
        ComplicationType.ICON,
        ComplicationType.SMALL_IMAGE
      )
    ),
    ComplicationSlot(
      "Background",
      4,
      arrayOf(ComplicationType.LARGE_IMAGE)
    )
  )
  override val apk: String
    get() = getApksBaseDir()!!.resolve("ComplicationWatchFace.apk").toString()
  override val appId = "androidx.wear.watchface.samples.app"
  override val watchFaceFQName = "androidx.wear.watchface.samples.ExampleCanvasDigitalWatchFaceService"
}