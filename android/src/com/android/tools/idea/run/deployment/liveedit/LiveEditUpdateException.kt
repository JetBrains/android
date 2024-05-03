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
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

class LiveEditUpdateException private constructor(val error: Error, val details: String = "", val sourceFilename: String?, cause : Throwable?) : RuntimeException(details, cause) {

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
    GRADLE_BUILD_FILE("Gradle build file changes", "%", false, Status.NON_KOTLIN),
    KOTLIN_EAP("Compilation Error", "%", true, Status.KOTLIN_EAP),
    NON_KOTLIN("Modifying a non-Kotlin file is not supported", "%", false, Status.NON_KOTLIN),
    NON_PRIVATE_INLINE_FUNCTION("Modified function is a non-private inline function", "%", true, Status.NON_PRIVATE_INLINE_FUNCTION),
    UNABLE_TO_INLINE("Unable to inline function", "%", true, Status.UNABLE_TO_INLINE),
    UNABLE_TO_LOCATE_COMPOSE_GROUP("Unable to locate Compose Invalid Group", "%", false, Status.UNABLE_TO_LOCATE_COMPOSE_GROUP),
    UNSUPPORTED_BUILD_SRC_CHANGE("buildSrc/ sources not supported", "%", false, Status.UNSUPPORTED_BUILD_SRC_CHANGE),

    UNSUPPORTED_SRC_CHANGE_ACCESS_ADDED("Unsupported change", "%", false, Status.UNSUPPORTED_ADDED_ACCESS),
    UNSUPPORTED_SRC_CHANGE_ACCESS_REMOVED("Unsupported change", "%", false, Status.UNSUPPORTED_REMOVED_ACCESS),
    UNSUPPORTED_SRC_CHANGE_CONSTRUCTOR("Unsupported change", "%", false, Status.UNSUPPORTED_CONSTRUCTOR),
    UNSUPPORTED_SRC_CHANGE_CLINIT("Unsupported change", "%", false, Status.UNSUPPORTED_CLINIT),
    UNSUPPORTED_SRC_CHANGE_ENCLOSING_METHOD("Unsupported change", "%", false, Status.UNSUPPORTED_ENCLOSING_METHOD),
    UNSUPPORTED_SRC_CHANGE_FIELD_ADDED("Unsupported change", "%", false, Status.UNSUPPORTED_ADDED_FIELD),
    UNSUPPORTED_SRC_CHANGE_INTERFACE("Unsupported change", "%", false, Status.UNSUPPORTED_INTERFACE),
    UNSUPPORTED_SRC_CHANGE_INIT("Unsupported change", "%", false, Status.UNSUPPORTED_INIT),
    UNSUPPORTED_SRC_CHANGE_FIELD_REMOVED("Unsupported change", "%", false, Status.UNSUPPORTED_REMOVED_FIELD),
    UNSUPPORTED_SRC_CHANGE_FIELD_MODIFIED("Unsupported change", "%", false, Status.UNSUPPORTED_MODIFIED_FIELD),
    UNSUPPORTED_SRC_CHANGE_METHOD_ADDED("Unsupported change", "added method(s): %", false, Status.UNSUPPORTED_ADDED_METHOD),
    UNSUPPORTED_SRC_CHANGE_METHOD_REMOVED("Unsupported change", "removed method(s): %", false, Status.UNSUPPORTED_REMOVED_METHOD),
    UNSUPPORTED_SRC_CHANGE_SIGNATURE("Unsupported change", "%", false, Status.UNSUPPORTED_SIGNATURE),
    UNSUPPORTED_SRC_CHANGE_SUPER_CLASS("Unsupported change", "%", false, Status.UNSUPPORTED_SUPER_CLASS),
    UNSUPPORTED_SRC_CHANGE_USER_CLASS_ADDED("Unsupported change", "%", false, Status.UNSUPPORTED_ADDED_CLASS),
    UNSUPPORTED_SRC_CHANGE_WHEN_ENUM_PATH("Unsupported change", "%", false, Status.UNSUPPORTED_WHEN_ENUM_PATH),

    UNSUPPORTED_TEST_SRC_CHANGE("Test sources not supported", "%", false, Status.UNSUPPORTED_TEST_SRC_CHANGE),
    UNABLE_TO_DESUGAR("Live Edit post-processing failure", "%", false, Status.UNABLE_TO_DESUGAR),
    UNSUPPORTED_BUILD_LIBRARY_DESUGAR("Live Edit post-processing failure", "%", false, Status.UNSUPPORTED_BUILD_LIBRARY_DESUGAR),
    VIRTUAL_FILE_NOT_EXIST("Modifying virtual file that does not exist", "%", false, Status.VIRTUAL_FILE_NOT_EXIST),
    BAD_MIN_API("Live Edit min-api detection failure", "%", false, Status.BAD_MIN_API),

    INTERNAL_ERROR_NO_COMPILER_OUTPUT("Internal Error", "%", false, Status.INTERNAL_ERROR_NO_COMPILER_OUTPUT),
    INTERNAL_ERROR_FILE_OUTSIDE_MODULE("Internal Error", "%", false, Status.INTERNAL_ERROR_FILE_OUTSIDE_MODULE),
    INTERNAL_ERROR_FILE_CODE_GEN("Internal Error", "%", false, Status.INTERNAL_ERROR_FILE_CODE_GEN),
    INTERNAL_ERROR_FILE_COMPILE_COMMAND_EXCEPTION("Internal Error", "%", false, Status.INTERNAL_ERROR_FILE_COMPILE_COMMAND_EXCEPTION),
    INTERNAL_ERROR_FILE_MULTI_MODULE("Internal Error", "%", false, Status.INTERNAL_ERROR_FILE_MULTI_MODULE),
  }

  companion object {
    // Sorted lexicographically for readability and consistency

    fun analysisError(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.ANALYSIS_ERROR, details, source?.name, cause)

    @JvmStatic
    fun compilationError(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.COMPILATION_ERROR, details, source?.name, cause)

    fun gradleBuildFile(source: PsiFile? = null) =
      LiveEditUpdateException(Error.GRADLE_BUILD_FILE, "Modification of Gradle build file not supported", source?.name, null)

    fun internalErrorCodeGenException(file: PsiFile, cause: Throwable) =
      LiveEditUpdateException(Error.INTERNAL_ERROR_FILE_CODE_GEN, "Internal Error During Code Gen", file.name, cause)

    fun internalErrorCompileCommandException(file: PsiFile, cause: Throwable) =
      LiveEditUpdateException(Error.INTERNAL_ERROR_FILE_COMPILE_COMMAND_EXCEPTION, "Unexpected error during compilation command", file.name, cause)

    fun internalErrorMultiModule(modules: Set<Module?>) =
      LiveEditUpdateException(Error.INTERNAL_ERROR_FILE_MULTI_MODULE,
                              "Multiple modules request [${modules.joinToString(",")}]", null, null)

    fun internalErrorNoCompilerOutput(file: PsiFile) =
      LiveEditUpdateException(Error.INTERNAL_ERROR_NO_COMPILER_OUTPUT, "No compiler output", file.name, null)

    fun internalErrorFileOutsideModule(file: PsiFile) =
      LiveEditUpdateException(Error.INTERNAL_ERROR_FILE_OUTSIDE_MODULE, "KtFile outside targeted module found in code generation", file.name, null)

    fun kotlinEap() = LiveEditUpdateException(Error.KOTLIN_EAP,"Live Edit does not support running with this Kotlin Plugin version"+
                                                               " and will only work with the bundled Kotlin Plugin", null, null)

    fun nonKotlin(file: PsiFile) = LiveEditUpdateException(Error.NON_KOTLIN, "", file.name, cause = null)

    fun unsupportedSourceModificationAddedMethod(location: String, msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_METHOD_ADDED, msg, location, null)

    fun unsupportedSourceModificationRemovedMethod(location: String, msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_METHOD_REMOVED, msg, location, null)

    fun unsupportedSourceModificationAddedAccess(location: String, msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_ACCESS_ADDED, msg, location, null)

    fun unsupportedSourceModificationRemovedAccess(location: String, msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_ACCESS_REMOVED, msg, location, null)

    fun unsupportedSourceModificationAddedField(location: String, msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_FIELD_ADDED, msg, location, null)

    fun unsupportedSourceModificationRemovedField(location: String, msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_FIELD_REMOVED, msg, location, null)

    fun unsupportedSourceModificationModifiedField(location: String, msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_FIELD_MODIFIED, msg, location, null)

    fun unsupportedSourceModificationSignature(location: String, msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_SIGNATURE, msg, location, null)

    fun unsupportedSourceModificationSuperClass(location: String, msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_SUPER_CLASS, msg, location, null)

    fun unsupportedSourceModificationInterface(location: String, msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_INTERFACE, msg, location, null)

    fun unsupportedSourceModificationEnclosingMethod(location: String, msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_ENCLOSING_METHOD, msg, location, null)

    fun unsupportedSourceModificationAddedUserClass(msg: String, file: PsiFile) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_USER_CLASS_ADDED, msg, file?.name, null)

    fun unsupportedSourceModificationWhenEnumPath(msg: String, file: PsiFile) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_WHEN_ENUM_PATH, msg, file?.name, null)

    fun unsupportedSourceModificationConstructor(msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_CONSTRUCTOR, msg, null, null)

    fun unsupportedSourceModificationClinit(msg: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_CLINIT, msg, null, null)

    fun unsupportedSourceModificationInit(msg: String, file: PsiFile) =
      LiveEditUpdateException(Error.UNSUPPORTED_SRC_CHANGE_INIT, msg, file?.name, null)

    fun unsupportedBuildSrcChange(name: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_BUILD_SRC_CHANGE, name, null, null)

    fun unsupportedTestSrcChange(name: String) =
      LiveEditUpdateException(Error.UNSUPPORTED_TEST_SRC_CHANGE, name, null, null)

    /**
     * We are unable to locate the Invalidate Group ID of a given Composable function's offsets.
     * This is unlikely to happen unless the Compose compiler changes how the offset-to-ID mapping works.
     */
    fun noInvalidateGroup(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.UNABLE_TO_LOCATE_COMPOSE_GROUP, details, source?.name, cause)

    fun inlineFailure(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.UNABLE_TO_INLINE, "$details", source?.name, cause)

    fun nonPrivateInlineFunctionFailure(source: PsiFile? = null) =
      LiveEditUpdateException(Error.NON_PRIVATE_INLINE_FUNCTION, "Inline functions visible outside of the file cannot be live edited. " +
                                                                 "Application needs to be rebuild.", source?.name, null)

    fun desugarFailure(details: String, file: String? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.UNABLE_TO_DESUGAR, details, file, cause)

    fun buildLibraryDesugarFailure(details: String, cause: Throwable? = null) =
      LiveEditUpdateException(Error.UNSUPPORTED_BUILD_LIBRARY_DESUGAR, details, null, cause)

    fun badMinAPIError(details: String, cause: Throwable? = null) = LiveEditUpdateException(Error.BAD_MIN_API, details, null, cause)

    fun virtualFileNotExist(virtualFile: VirtualFile, file: PsiFile) =
      LiveEditUpdateException(Error.VIRTUAL_FILE_NOT_EXIST, details = "deleted Kotlin file ${virtualFile.path}", sourceFilename = file?.name, cause = null)
  }

  fun isCompilationError() : Boolean {
    return when (error) {
      Error.ANALYSIS_ERROR -> message?.startsWith("Analyze Error.") ?: false
      Error.COMPILATION_ERROR -> true
      else -> false
    }
  }
}

