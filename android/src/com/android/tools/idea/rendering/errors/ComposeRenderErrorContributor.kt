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

  private fun isViewModelStackTrace(throwable: Throwable?): Boolean = throwable?.let { throwable ->
    throwable.stackTrace.any {
      (it.methodName == "viewModel" || it.className.endsWith("ViewModelProvider") || it.className.endsWith("ViewModelKt"))
      && it.className.startsWith("androidx.lifecycle")
    }
  } ?: false

  /**
   * Returns true if the [Throwable] represents a failure to instantiate a Preview Composable. This means that the user probably
   * added one or more previews and a build is needed.
   */
  private fun isComposeNotFoundThrowable(throwable: Throwable?): Boolean {
    return throwable is NoSuchMethodException && throwable.getStackTrace()[1].methodName.startsWith("invokeComposableViaReflection")
  }

  /**
   * Returns true if the [Throwable] represents a failure to instantiate a Preview Composable with `PreviewParameterProvider`. This will
   * detect the case where the parameter type does not match the `PreviewParameterProvider`.
   */
  private fun isPreviewParameterMismatchThrowable(throwable: Throwable?): Boolean {
    return throwable is IllegalArgumentException &&
           throwable.message == "argument type mismatch" &&
           (throwable.stackTrace.drop(5)
             .firstOrNull()?.methodName?.startsWith("invokeComposable") ?: false)
  }

  /**
   * Returns true if [throwable] is a [NoSuchMethodException] that fails to find a method called `FailToLoadPreviewParameterProvider`. This
   * is a fake name defined in `ComposePreviewElement`, and we use it as a fake PreviewElement name when there is a failure to load a
   * `PreviewParameterProvider`, otherwise the crash will cause no previews to be displayed. Instead, we want to display a Preview
   * containing errors and let the user know there was an error to load their PreviewParameterProvider.
   */
  private fun isFailToLoadPreviewParameterProvider(throwable: Throwable?): Boolean {
    return throwable is NoSuchMethodException && throwable.message?.endsWith("${'$'}FailToLoadPreviewParameterProvider") == true
  }

  @JvmStatic
  fun isHandledByComposeContributor(throwable: Throwable?): Boolean =
    isComposeNotFoundThrowable(throwable) ||
    isCompositionLocalStackTrace(throwable) ||
    isPreviewParameterMismatchThrowable(throwable) ||
    isFailToLoadPreviewParameterProvider(throwable) ||
    isViewModelStackTrace(throwable) // Keep this one as last, as it needs to visit multiple stack trace elements

  @JvmStatic
  fun reportComposeErrors(logger: RenderLogger,
                          linkManager: HtmlLinkManager,
                          linkHandler: HyperlinkListener): List<RenderErrorModel.Issue> =
    logger.messages
      .filter { it.tag == ILayoutLog.TAG_INFLATE }
      .mapNotNull {
        when {
          isViewModelStackTrace(it.throwable) -> {
            RenderErrorModel.Issue.builder()
              .setSeverity(HighlightSeverity.INFORMATION)
              .setSummary("Failed to instantiate a ViewModel")
              .setHtmlContent(HtmlBuilder()
                                .addLink("This preview uses a ", "ViewModel", ". ",
                                         "https://developer.android.com/topic/libraries/architecture/viewmodel")
                                .add("ViewModels often trigger operations not supported by Compose Preview, " +
                                     "such as database access, I/O operations, or network requests. ")
                                .addLink("You can ", "read more", " about preview limitations in our external documentation.",
                                          // TODO(b/199834697): add correct header once the ViewModel documentation is published on DAC
                                         "https://developer.android.com/jetpack/compose/tooling")
                                .addShowException(linkManager, logger.module?.project, it.throwable)
              )
          }
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
          isPreviewParameterMismatchThrowable(it.throwable) -> {
            RenderErrorModel.Issue.builder()
              .setSeverity(HighlightSeverity.ERROR)
              .setSummary("PreviewParameterProvider/@Preview type mismatch.")
              .setHtmlContent(
                HtmlBuilder()
                  .add("The type of the PreviewParameterProvider must match the @Preview input parameter type annotated with it.")
                  .addBuildAction(linkManager)
              )
          }
          isFailToLoadPreviewParameterProvider(it.throwable) -> {
            RenderErrorModel.Issue.builder()
              .setSeverity(HighlightSeverity.ERROR)
              .setSummary("Fail to load PreviewParameterProvider")
              .setHtmlContent(
                HtmlBuilder()
                  .add("There was problem to load the PreviewParameterProvider defined. Please double-check its constructor and the " +
                       "values property implementation. The IDE logs should contain the full exception stack trace.")
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