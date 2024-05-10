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
package com.android.tools.idea.rendering.errors

import com.android.SdkConstants
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.android.tools.rendering.HtmlLinkManager
import com.android.tools.rendering.RenderLogger
import com.android.utils.HtmlBuilder
import com.intellij.lang.annotation.HighlightSeverity
import javax.swing.event.HyperlinkListener

object WearTileRenderErrorContributor {

  /**
   * This method determines if [throwable] is an error that was caused by a Wear Tile Preview and
   * that can be reported on.
   */
  @JvmStatic
  fun isHandledByWearTileContributor(throwable: Throwable?) = isWrongUseOfContext(throwable)

  /**
   * This method reports errors caused by Wear Tile Previews. This method should be called if
   * [isHandledByWearTileContributor] returns true.
   */
  @JvmStatic
  fun reportWearTileErrors(
    logger: RenderLogger,
    linkManager: HtmlLinkManager,
    linkHandler: HyperlinkListener,
  ): List<RenderErrorModel.Issue> =
    logger.brokenClasses
      .mapNotNull {
        when {
          isWrongUseOfContext(it.value) ->
            RenderErrorModel.Issue.builder()
              .setSeverity(HighlightSeverity.ERROR)
              .setSummary("Invalid Context used")
              .setHtmlContent(
                HtmlBuilder()
                  .addLink(
                    "It seems like the Tile Preview failed to render due to the ",
                    "Context",
                    ". ",
                    "https://developer.android.com/reference/android/content/Context",
                  )
                  .add(
                    "Any Context used within a preview must come from the preview method's parameter, otherwise it will not be properly initialised."
                  )
                  .newlineIfNecessary()
                  .addExceptionMessage(linkManager, it.value)
              )
          else -> null
        }
      }
      .map { it.setLinkHandler(linkHandler).build() }

  /**
   * Detects if the wrong Context was used. Users should use the Context provided by the preview
   * method parameter, and not other ones, such as the context from a TileService.
   */
  private fun isWrongUseOfContext(throwable: Throwable?) =
    throwable?.let {
      throwable.stackTrace.any { it.className.startsWith(SdkConstants.CLASS_CONTEXT) } &&
        throwable.stackTrace.any { it.className == SdkConstants.CLASS_TILE_SERVICE_VIEW_ADAPTER }
    } ?: false
}
