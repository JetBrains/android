/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.validation.validators

import com.android.repository.io.FileOp
import com.android.repository.io.FileOpUtils
import com.android.tools.adtui.validation.Validator
import com.android.tools.adtui.validation.Validator.Result
import com.android.tools.adtui.validation.Validator.Severity
import com.google.common.base.CharMatcher
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import net.jcip.annotations.Immutable
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.util.Locale

private val logger: Logger get() = logger<PathValidator>()

/**
 * A class which contains validation logic that should be run on a path to ensure that there won't
 * be any problems with using it. If there is an issue, we offer a readable string which we can show to the user.
 *
 * Use [validate] to test a path and check [Result.getSeverity] to see if there was a warning or error.
 *
 * The convenience method [createDefault] is provided for creating a validator for the most common cases.
 */
@Immutable
class PathValidator
/**
 * Constructs a class that will validate a path against the various passed in rules, returning
 * a readable message if something goes wrong. A name describing the purpose of the path should
 * be included as it will be used in the error messages when applicable.
 */ private constructor(private val pathName: String,
                        @get:TestOnly val errors: Iterable<Rule>,
                        private val warnings: Iterable<Rule>,
                        private val fileOp: FileOp) : Validator<File> {
  /**
   * Validate that the target location passes all tests.
   *
   * @return [Result.OK] or the first error or warning it encounters.
   */
  override fun validate(file: File): Result =
    try {
      validate(file, Severity.ERROR).takeIf { it != Result.OK } ?: validate(file, Severity.WARNING).takeIf { it != Result.OK } ?: Result.OK
    }
    catch (ex: Exception) {
      logger.warn(ex)
      Result(Severity.ERROR, "Invalid file, see Help -> Show Log for more details: $file")
    }

  /**
   * Run only the validations whose level match the passed in [Severity].
   */
  private fun validate(projectFile: File, severity: Severity): Result {
    assert(severity != Severity.OK)
    val rules = if (severity == Severity.ERROR) errors else warnings

    for (rule in rules) {
      val matchingFile = rule.getMatchingFile(fileOp, projectFile)
      if (matchingFile != null) {
        return Result(severity, rule.getMessage(matchingFile, pathName))
      }
    }
    return Result.OK
  }

  class Builder {
    private val errors = mutableListOf<Rule>()
    private val warnings = mutableListOf<Rule>()
    /**
     * Useful for creating a [PathValidator] with all rules enforced.
     */
    fun withAllRules(): Builder {
      // Note: Order of rules is important, we want to check for invalid slashes, chars, etc before checking if we can write
      withCommonRules()
      withError(IS_EMPTY)
      withError(PATH_NOT_WRITABLE)
      withWarning(NON_EMPTY_DIRECTORY)
      return this
    }

    /**
     * Useful for creating a [PathValidator] with most rules enforced.
     */
    fun withCommonRules(): Builder {
      withCommonTestRules()
      if (SystemInfo.isWindows) {
        withError(WINDOWS_PATH_TOO_LONG)
      }
      return this
    }

    /**
     * Contains Common rules but excluding the ones that will not pass build bot.
     */
    @TestOnly
    fun withCommonTestRules(): Builder {
      withError(INVALID_SLASHES)
      withError(ILLEGAL_CHARACTER)
      withWarning(WHITESPACE)
      if (SystemInfo.isWindows) {
        withError(ILLEGAL_WINDOWS_FILENAME)
        withError(NON_ASCII_CHARS)
      } else {
        withWarning(ILLEGAL_WINDOWS_FILENAME)
        withWarning(NON_ASCII_CHARS)
      }
      withError(PARENT_DIRECTORY_NOT_WRITABLE)
      withError(LOCATION_IS_A_FILE)
      withError(LOCATION_IS_ROOT)
      withError(PARENT_IS_NOT_A_DIRECTORY)
      withError(PATH_INSIDE_ANDROID_STUDIO)
      return this
    }


    fun withError(rule: Rule) = this.apply { errors.add(rule) }

    fun withWarning(rule: Rule) = this.apply { warnings.add(rule) }

    @JvmOverloads
    fun build(pathName: String, fileOp: FileOp = FileOpUtils.create()): PathValidator = PathValidator(pathName, errors, warnings, fileOp)
  }

  companion object {
    /**
     * A validator that provides reasonable defaults for checking a path's validity.
     *
     * @param pathName name of the path being validated. Used inside [Validator.Result]'s message.
     */
    @JvmStatic
    fun createDefault(pathName: String): PathValidator = Builder().withAllRules().build(pathName)
  }
}

/**
 * A single validation ruleModuleValidatorTest which tests a target file to see if it violates it.
 */
interface Rule {
  /**
   * Returns a [File] which violates this rule or `null` if none.
   *
   * If a file is returned, it will usually be the same as `file` but not always.
   * For example, a rule may complain about a file's parent.
   */
  fun getMatchingFile(fileOp: FileOp, file: File): File?

  fun getMessage(file: File, fieldName: String): String
}

/**
 * A rule which is run on the target file as is.
 */
private fun createSimpleRule(
  matches: (FileOp, File) -> Boolean,
  getMessage: (File, String) -> String
) = object : Rule {
  override fun getMatchingFile(fileOp: FileOp, file: File) = file.takeIf { matches(fileOp, file) }
  override fun getMessage(file: File, fieldName: String) = getMessage(file, fieldName)
}

/**
 * A rule which is run on the target file and each of its ancestors, recursively.
 */
private fun createRecursiveRule(
  matches: (FileOp, File) -> Boolean,
  getMessage: (File, String) -> String
) = object : Rule {
  override fun getMatchingFile(fileOp: FileOp, file: File) = generateSequence(file) { it.parentFile }.firstOrNull { matches(fileOp, it) }
  override fun getMessage(file: File, fieldName: String) = getMessage(file, fieldName)
}

private val RESERVED_WINDOWS_FILENAMES: Set<String> = setOf(
  "con", "prn", "aux", "clock$", "nul",
  "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
  "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
  "\$mft", "\$mftmirr", "\$logfile", "\$volume", "\$attrdef", "\$bitmap", "\$boot",
  "\$badclus", "\$secure", "\$upcase", "\$extend", "\$quota", "\$objid", "\$reparse")

/**
 * Although Android Studio and Gradle support paths longer than MAX_PATH (260) on Windows,
 * there is still a limit to the length of the path to the root directory,
 * as each project directory contains a "gradlew.bat" file (used to build the project),
 * and the length of the path to the "gradlew.bat" cannot exceed MAX_PATH,
 * because Windows limits path of executables to MAX_PATH (for backward compatibility reasons).
 */
private const val WINDOWS_PATH_LENGTH_LIMIT = 240

private val ILLEGAL_CHARACTER_MATCHER: CharMatcher = CharMatcher.anyOf("[/\\\\?%*:|\"<>!;]")

val IS_EMPTY = createSimpleRule(
  { _, file -> file.name.isEmpty() },
  { _, fieldName -> "Please specify a $fieldName." }
)

val INVALID_SLASHES = createSimpleRule(
  { _, file -> File.separatorChar == '/' && file.path.contains("\\") || File.separatorChar == '\\' && file.path.contains("/") },
  { _, fieldName ->
    val incorrectSlash = if (File.separatorChar == '\\') '/' else '\\'
    "Your $fieldName contains incorrect slashes ('$incorrectSlash')."
  }
)

val LOCATION_IS_A_FILE = createSimpleRule(
  { fileOp, file -> fileOp.isFile(file) },
  { _, fieldName -> "The $fieldName specified already exists." }
)

val LOCATION_IS_ROOT = createSimpleRule(
  { _, file -> file.parent == null },
  { _, fieldName -> "The $fieldName cannot be at the filesystem root." }
)

val PARENT_IS_NOT_A_DIRECTORY = createSimpleRule(
  { fileOp, file ->
    val parent = file.parentFile
    parent != null && fileOp.exists(parent) && !fileOp.isDirectory(parent)
  },
  { _, fieldName -> "The $fieldName's parent must be a directory, not a plain file." }
)

val PATH_NOT_WRITABLE = createSimpleRule(
  { fileOp, file -> fileOp.exists(file) && !fileOp.canWrite(file) },
  { file, _ -> "The path '${file.path}' is not writable. Please choose a new location." }
)

val PATH_INSIDE_ANDROID_STUDIO = createSimpleRule(
  { _, file -> PathManager.getHomePathFor(Application::class.java)?.let { FileUtil.isAncestor(File(it), file, false) } ?: false },
  { _, fieldName -> "The $fieldName is inside ${ApplicationNamesInfo.getInstance().productName}'s install location." }
)

val NON_EMPTY_DIRECTORY = createSimpleRule(
  { fileOp, file -> fileOp.listFiles(file).isNotEmpty() },
  { file, fieldName -> "'${file.name}' already exists at the specified $fieldName." }
)

val WINDOWS_PATH_TOO_LONG = createSimpleRule(
  { _, file -> file.absolutePath.length > WINDOWS_PATH_LENGTH_LIMIT },
  { _, fieldName -> "The length of the $fieldName exceeds the limit of $WINDOWS_PATH_LENGTH_LIMIT characters." }
)

val WHITESPACE = createRecursiveRule(
  { _, file -> CharMatcher.whitespace().matchesAnyOf(file.name) },
  { _, fieldName -> "$fieldName should not contain whitespace, as this can cause problems with the NDK tools." }
)

val NON_ASCII_CHARS = createRecursiveRule(
  { _, file -> !CharMatcher.ascii().matchesAllOf(file.name) },
  { _, fieldName -> "Your $fieldName contains non-ASCII characters." }
)

val PARENT_DIRECTORY_NOT_WRITABLE = createRecursiveRule(
  { fileOp, file ->
    val parent = file.parentFile
    !fileOp.exists(file) && parent != null && fileOp.exists(parent) && !fileOp.canWrite(parent)
  },
  { file, _ -> "The path '${file.parentFile.path}' is not writable. Please choose a new location." }
)

/**
 * Note: This should be an error only under Windows (for other platforms should be a warning)
 */
val ILLEGAL_WINDOWS_FILENAME = createRecursiveRule(
  { _, file -> RESERVED_WINDOWS_FILENAMES.contains(file.name.toLowerCase(Locale.US)) },
  { file, fieldName -> "Illegal (Windows) filename in $fieldName path: ${file.name}." }
)

val ILLEGAL_CHARACTER = createRecursiveRule(
  { _, file -> ILLEGAL_CHARACTER_MATCHER.matchesAnyOf(file.name) },
  { file, fieldName ->
    val name = file.name
    val illegalChar = name[ILLEGAL_CHARACTER_MATCHER.indexIn(name)]
    "Illegal character in $fieldName path: '$illegalChar' in filename $name."
  }
)
