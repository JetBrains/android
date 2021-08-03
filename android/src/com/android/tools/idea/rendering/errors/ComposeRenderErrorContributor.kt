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
package com.android.tools.idea.rendering.errors

import com.android.ide.common.rendering.api.ILayoutLog
import com.android.tools.idea.rendering.HtmlLinkManager
import com.android.tools.idea.rendering.RenderLogger
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.android.utils.HtmlBuilder
import com.intellij.lang.annotation.HighlightSeverity
import javax.swing.event.HyperlinkListener

object ComposeRenderErrorContributor {
  private fun isCompositionLocalStackTrace(throwable: Throwable?): Boolean = throwable?.let { throwable ->
    throwable is IllegalStateException &&
    throwable.stackTrace.any {
      (it.methodName == "resolveCompositionLocal" || it.methodName == "CompositionLocalProvider")
      && it.className.startsWith("androidx.compose.runtime")
    }
  } ?: false

  /**
   * Returns true if the [Throwable] represents a failure to instantiate a Preview Composable. This means that the user probably
   * added one or more previews and a build is needed.
   */
  private fun isComposeNotFoundThrowable(throwable: Throwable?): Boolean {
    return throwable is NoSuchMethodException && throwable.getStackTrace()[1].methodName.startsWith("invokeComposableViaReflection")
  }

  @JvmStatic
  fun isHandledByComposeContributor(throwable: Throwable?): Boolean =
    isComposeNotFoundThrowable(throwable) ||
    isCompositionLocalStackTrace(throwable)

  @JvmStatic
  fun reportComposeErrors(logger: RenderLogger,
                          linkManager: HtmlLinkManager,
                          linkHandler: HyperlinkListener): List<RenderErrorModel.Issue> =
    logger.messages
      .filter { it.tag == ILayoutLog.TAG_INFLATE }
      .mapNotNull {
        when {
          isCompositionLocalStackTrace(it.throwable) -> {
            RenderErrorModel.Issue.builder()
              .setSeverity(HighlightSeverity.INFORMATION)
              .setSummary("Failed to instantiate Composition Local")
              .setHtmlContent(HtmlBuilder()
                                .addLink("This preview was unable to find a ", "CompositionLocal", ". ",
                                         "https://developer.android.com/jetpack/compose/compositionlocal")
                                .add("You might need to define it so it can render correctly.")
                                .addShowException(linkManager, logger.module?.project, it.throwable)
              )
          }
          isComposeNotFoundThrowable(it.throwable) -> {
            // This is a Compose not found error. This is not a high severity error so transform to a warning.
            RenderErrorModel.Issue.builder()
              .setSeverity(HighlightSeverity.WARNING)
              .setSummary("Unable to find @Preview '" + it.throwable!!.message + "'")
              .setHtmlContent(
                HtmlBuilder()
                  .add("The preview will display after rebuilding the project.")
                  .addBuildAction(linkManager)
              )
          }
          else -> null
        }
      }.map {
        it
          .setLinkHandler(linkHandler)
          .build()
      }
}