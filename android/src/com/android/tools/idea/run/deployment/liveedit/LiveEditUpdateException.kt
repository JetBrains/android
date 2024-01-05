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
package com.android.tools.idea.run.deployment.liveedit

import com.google.wireless.android.sdk.stats.LiveEditEvent.Status
import com.intellij.psi.PsiFile

class LiveEditUpdateException(val error: Error, val details: String = "", val source: PsiFile?, cause : Throwable?) : RuntimeException(details, cause) {

  /**
   * @param message Short description
   * @param details Detailed information of the error if available.
   * @param recoverable If this flag is flags, the current deployment of the application can no longer be live edited and
   *                    a build and re-run would be required for future live edits.
   */
  enum class Error (val message: String, val details: String = "", val recoverable: Boolean = true,
                    val metric : Status = Status.UNKNOWN) {
    // Sorted lexicographically for readability and consistency
    ANALYSIS_ERROR("Resolution Analysis Error", "%", true, Status.ANALYSIS_ERROR),
    COMPILATION_ERROR("Compilation Error", "%", true, Status.COMPILATION_ERROR),
    NON_KOTLIN("Modifying a non-Kotlin file is not supported", "%", false, Status.NON_KOTLIN),
    NON_PRIVATE_INLINE_FUNCTION("Modified function is a non-private inline function", "%", true, Status.NON_PRIVATE_INLINE_FUNCTION),
    UNABLE_TO_INLINE("Unable to inline function", "%", true, Status.UNABLE_TO_INLINE),
    UNABLE_TO_LOCATE_COMPOSE_GROUP("Unable to locate Compose Invalid Group", "%", false, Status.UNABLE_TO_LOCATE_COMPOSE_GROUP),
    UNSUPPORTED_BUILD_SRC_CHANGE("buildSrc/ sources not supported", "%", false, Status.UNSUPPORTED_BUILD_SRC_CHANGE),
    UNSUPPORTED_SRC_CHANGE_RECOVERABLE("Unsupported change", "%", true, Status.UNSUPPORTED_SRC_CHANGE_RECOVERABLE),
    UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE("Unsupported change", "%", false, Status.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE),
    UNSUPPORTED_TEST_SRC_CHANGE("Test sources not supported", "%", false, Status.UNSUPPORTED_TEST_SRC_CHANGE),
    UNABLE_TO_DESUGAR("Live Edit post-processing failure", "%", false, Status.UNABLE_TO_DESUGAR),
    UNSUPPORTED_BUILD_LIBRARY_DESUGAR("Live Edit post-processing failure", "%", false, Status.UNSUPPORTED_BUILD_LIBRARY_DESUGAR),
    BAD_MIN_API("Live Edit min-api detection failure", "%", false, Status.BAD_MIN_API),

    INTERNAL_ERROR("Internal Error", "%", false, Status.INTERNAL_ERROR),
    INTERNAL_ERROR_NO_BINDING_CONTEXT("Internal Error", "%", false, Status.INTERNAL_ERROR), // TODO: Separate Tracking Metrics.

    KNOWN_ISSUE("Known Issue", "%", true, Status.KNOWN_ISSUE),
  }

  companion object {
    // Sorted lexicographically for readability and consistency

    fun analysisError(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.ANALYSIS_ERROR, details, source, cause)

    @JvmStatic
    fun compilationError(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.COMPILATION_ERROR, details, source, cause)

    fun internalError(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.INTERNAL_ERROR, details, source, cause)

    fun internalError(details: String, cause: Throwable? = null) =
      LiveEditUpdateException(Error.INTERNAL_ERROR, details, null, cause)

    fun internalErrorNoBindContext(details: String) =
      LiveEditUpdateException(Error.INTERNAL_ERROR_NO_BINDING_CONTEXT, details, null, null)

    fun nonKotlin(file: PsiFile) =
      LiveEditUpdateException(Error.NON_KOTLIN, source = file, cause = null)

    fun unsupportedUnrecoverableSourceModification(type: String, file: PsiFile) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE, type, file, null)

    fun unsupportedRecoverableSourceModification(type: String, file: PsiFile) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_RECOVERABLE, type, file, null)

    fun unsupportedBuildSrcChange(name: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_BUILD_SRC_CHANGE, name, null, null)

    fun unsupportedTestSrcChange(name: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_TEST_SRC_CHANGE, name, null, null)

    /**
     * We are unable to locate the Invalidate Group ID of a given Composable function's offsets.
     * This is unlikely to happen unless the Compose compiler changes how the offset-to-ID mapping works.
     */
    fun noInvalidateGroup(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.UNABLE_TO_LOCATE_COMPOSE_GROUP, details, source, cause)

    fun inlineFailure(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.UNABLE_TO_INLINE, "$details", source, cause)

    fun nonPrivateInlineFunctionFailure(source: PsiFile? = null) =
      LiveEditUpdateException(Error.NON_PRIVATE_INLINE_FUNCTION, "Inline functions visible outside of the file cannot be live edited. " +
                                                                 "Application needs to be rebuild.", source, null)

    fun desugarFailure(details: String, cause: Throwable? = null) {
      throw LiveEditUpdateException(Error.UNABLE_TO_DESUGAR, details, null, cause)
    }

    fun buildLibraryDesugarFailure(details: String, cause: Throwable? = null) {
      throw LiveEditUpdateException(Error.UNSUPPORTED_BUILD_LIBRARY_DESUGAR, details, null, cause)
    }

    fun badMinAPIError(details: String, cause: Throwable? = null) {
      throw LiveEditUpdateException(Error.BAD_MIN_API, details, null, cause)
    }
  }
}

