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

import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.desugarFailure
import com.android.tools.idea.run.deployment.liveedit.desugaring.MinApiLevel
import com.android.tools.r8.ExtractMarker
import com.android.tools.r8.ExtractMarkerCommand
import java.nio.file.Path
import kotlin.io.path.absolute

// We store here all information we need when an app is deployed to a device.
class LiveEditApp(private val apks: Set<Path>, private val deviceMinAPI: MinApiLevel) {

  val minAPI : MinApiLevel by lazy(LazyThreadSafetyMode.NONE) { calculateMinAPI(apks) }
  private val logger = LiveEditLogger("LE App")

  private fun calculateMinAPI(apks: Set<Path>) : MinApiLevel {
    val start = System.nanoTime()
    val minApis : MutableSet<MinApiLevel> = mutableSetOf()
    apks.forEach{
      val apk = it
      logger.log("Searching marker for apk '${apk.absolute()}'")
      val consumer = LiveEditMarkerInfoConsumer()
      ExtractMarker.run(ExtractMarkerCommand.builder().addProgramFiles(apk).setMarkerInfoConsumer(consumer).build())
      minApis.add(consumer.minApi)
    }

    if (minApis.size > 1) {
      desugarFailure("Too many minAPI from APKs=$apks, minAPIs extracted=$minApis")
    }

    if (minApis.isEmpty()) {
      logger.log("APks $apks did not contain R8 markers (not desugared?). Falling back to api=$deviceMinAPI")
      minApis.add(deviceMinAPI)
    }

    val duration = (System.nanoTime() - start) / 1_000_000
    logger.log("Found minAPI = $minApis in ${duration}ms")
    return minApis.first()
  }
}