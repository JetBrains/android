/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.preview

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.util.asLogString
import com.android.tools.idea.run.util.StopWatch
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.diagnostic.Logger
import java.util.UUID

/**
 * A logger intended to be used in [updatePreviewsAndRefresh] that encapsulates complex debugging logic and abstracts out the
 * [PreviewElement] descendant features that might be used for logging. The users are supposed to inherit from the
 * [PreviewElementDebugLogger] and implement [logPreviewElement] for the specified [PreviewElement] descendant.
 */
class PreviewElementDebugLogger(private val log: Logger) {
  private val refreshId = UUID.randomUUID().toString()
  private val stopwatch = StopWatch()

  fun logPreviewElement(previewElementLogString: String, previewXmlContent: String){
    log("""Preview found at ${stopwatch.duration.toMillis()}ms
        $previewElementLogString

        $previewXmlContent
     """.trimIndent())
  }

  fun log(message: String) {
    log.debug("[$refreshId] $message")
  }

  fun logRenderComplete(surface: DesignSurface<LayoutlibSceneManager>) {
    log("Render completed in ${stopwatch.duration.toMillis()}ms")

    // Log any rendering errors
    surface.sceneManagers.forEach {
      val modelName = it.model.modelDisplayName
      it.renderResult?.let { result ->
        val renderLogger = result.logger
        log("""modelName="$modelName" result
                  | $result
                  | hasErrors=${renderLogger.hasErrors()}
                  | missingClasses=${renderLogger.missingClasses}
                  | messages=${renderLogger.messages.asLogString()}
                  | exceptions=${renderLogger.brokenClasses.values}
                """.trimMargin())
      }
    }
  }
}