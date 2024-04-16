/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.flags.StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_ALLOW_MULTIPLE_MIN_API_DEX_MARKERS_IN_APK
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.badMinAPIError
import com.android.tools.idea.run.deployment.liveedit.desugaring.ApiLevel
import com.android.tools.idea.run.deployment.liveedit.desugaring.MinApiLevel
import com.android.tools.r8.ExtractMarker
import com.android.tools.r8.ExtractMarkerCommand
import java.nio.file.Path

// We store here all information we need when an app is deployed to a device.
/**
 * @param buildSystemAPILevels minSdkVersionForDexing values from build system. Empty if that information isn't provided and we should
 *                        discover that from DEX file markers.
 */
class LiveEditApp(private val apks: Set<Path>,
                  private val deviceAPILevel: ApiLevel,
                  private val buildSystemAPILevels: MutableSet<MinApiLevel> = mutableSetOf()) {

  val minAPI : MinApiLevel by lazy(LazyThreadSafetyMode.NONE) { calculateMinAPI(apks) }
  private val logger = LiveEditLogger("LE App")

  // We store some events in a journal so we can output a proper error message in case of failure later.
  private val journal = mutableListOf<String>()

  private fun calculateMinAPI(apks: Set<Path>) : MinApiLevel {
    val start = System.nanoTime()
    val minApis : MutableSet<MinApiLevel> = if (buildSystemAPILevels.isEmpty()) {
      mutableSetOf<MinApiLevel>().apply {
        apks.forEach{ extractMinApiFromDexMarkers(it) }
      }
    } else {
      journal("Build system minSdkVersionForDexing = ${buildSystemAPILevels.joinToString(", ")}")
      buildSystemAPILevels
    }

    if (minApis.size > 1 && !COMPOSE_DEPLOY_LIVE_EDIT_ALLOW_MULTIPLE_MIN_API_DEX_MARKERS_IN_APK.get()) {
      throw badMinAPIError("Too many minAPI. Details:\n ${journal.joinToString("\n")}")
    }

    if (minApis.isEmpty()) {
      logger.log("APks $apks did not contain R8 markers (not desugared?). Falling back to device api=$deviceAPILevel")
      minApis.add(deviceAPILevel)
    }

    val duration = (System.nanoTime() - start) / 1_000_000
    logger.log("Found minAPI = $minApis in ${duration}ms")
    return minApis.minOf { it }
  }

  private fun MutableSet<MinApiLevel>.extractMinApiFromDexMarkers(apk: Path) {
    val consumer = LiveEditMarkerInfoConsumer()
    ExtractMarker.run(ExtractMarkerCommand.builder().addProgramFiles(apk).setMarkerInfoConsumer(consumer).build())
    journal("Apk '${apk.fileName}' contains minAPI = ${consumer.minApis}")
    this.addAll(consumer.minApis)
  }

  private fun journal(msg: String) {
    logger.log(msg)
    journal.add(msg)
  }
}