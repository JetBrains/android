/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.CharMatcher
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
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
 */ private constructor(private val myPathName: String,
                        @get:TestOnly val errors: Iterable<Rule>,
                        private val myWarnings: Iterable<Rule>,
                        private val myFileOp: FileOp) : Validator<File> {
  /**
   * Validate that the target location passes all tests.
   *
   * @return [Result.OK] or the first error or warning it encounters.
   */
  override fun validate(file: File): Result {
    try {
      var result = validate(file, Severity.ERROR)
      if (result !== Result.OK) {
        return result
      }
      result = validate(file, Severity.WARNING)
      if (result !== Result.OK) {
        return result
      }
    }
    catch (ex: Exception) {
      logger.warn(ex)
      return Result(Severity.ERROR, String.format("Invalid file, see Help -> Show Log for more details: %1\$s", file))
    }
    return Result.OK
  }

  /**
   * Run only the validations whose level match the passed in [Severity].
   */
  private fun validate(projectFile: File,
                       severity: Severity): Result {
    assert(severity != Severity.OK)
    val rules = if (severity == Severity.ERROR) errors else myWarnings
    for (rule in rules) {
      val matchingFile = rule.getMatchingFile(myFileOp, projectFile)
      if (matchingFile != null) {
        return Result(severity, rule.getMessage(matchingFile, myPathName))
      }
    }
    return Result.OK
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

  class Builder {
    private val myErrors: MutableList<Rule> = Lists.newArrayList()
    private val myWarnings: MutableList<Rule> = Lists.newArrayList()
    /**
     * Useful for creating a [PathValidator] with all rules enforced.
     */
    fun withAllRules(pathNotWritable: Severity): Builder {
      // Note: Order of rules is important, we want to check for invalid slashes, chars, etc before checking if we can write
      withCommonRules()
      withRule(IS_EMPTY, Severity.ERROR)
      withRule(PATH_NOT_WRITABLE, pathNotWritable)
      withRule(NON_EMPTY_DIRECTORY, Severity.WARNING)
      return this
    }

    /**
     * Useful for creating a [PathValidator] with most rules enforced.
     */
    fun withCommonRules(): Builder {
      withCommonTestRules()
      if (SystemInfo.isWindows) {
        withRule(WINDOWS_PATH_TOO_LONG, Severity.ERROR)
      }
      return this
    }

    /**
     * Contains Common rules but excluding the ones that will not pass build bot.
     * Only used for unit tests.
     */
    @VisibleForTesting
    fun withCommonTestRules(): Builder {
      withRule(INVALID_SLASHES, Severity.ERROR)
      withRule(ILLEGAL_CHARACTER, Severity.ERROR)
      withRule(ILLEGAL_WINDOWS_FILENAME, if (SystemInfo.isWindows) Severity.ERROR else Severity.WARNING)
      withRule(WHITESPACE, Severity.WARNING)
      withRule(NON_ASCII_CHARS, if (SystemInfo.isWindows) Severity.ERROR else Severity.WARNING)
      withRule(PARENT_DIRECTORY_NOT_WRITABLE, Severity.ERROR)
      withRule(LOCATION_IS_A_FILE, Severity.ERROR)
      withRule(LOCATION_IS_ROOT, Severity.ERROR)
      withRule(PARENT_IS_NOT_A_DIRECTORY, Severity.ERROR)
      withRule(PATH_INSIDE_ANDROID_STUDIO, Severity.ERROR)
      return this
    }

    /**
     * Add a [Rule] individually, in case [withCommonRules] is too aggressive for
     * your use-case, or if you want to add a custom one-off rule.
     */
    fun withRule(rule: Rule, severity: Severity): Builder {
      when (severity) {
        Severity.ERROR -> myErrors.add(rule)
        Severity.WARNING -> myWarnings.add(rule)
        else -> throw IllegalArgumentException(String.format("Can't create rule with invalid severity %1\$s", severity))
      }
      return this
    }

    @JvmOverloads
    fun build(pathName: String, fileOp: FileOp = FileOpUtils.create()): PathValidator {
      return PathValidator(pathName, myErrors, myWarnings, fileOp)
    }
  }

  /**
   * A rule which is run on the target file as is.
   */
  private abstract class SimpleRule : Rule {
    override fun getMatchingFile(fileOp: FileOp, file: File): File? {
      return if (matches(fileOp, file)) file else null
    }

    protected abstract fun matches(fileOp: FileOp, file: File): Boolean
  }

  /**
   * A rule which is run on the target file and each of its ancestors, recursively.
   */
  private abstract class RecursiveRule : Rule {
    override fun getMatchingFile(fileOp: FileOp, file: File): File? {
      var currFile: File? = file
      while (currFile != null) {
        if (matches(fileOp, currFile)) {
          return currFile
        }
        currFile = currFile.parentFile
      }
      return null
    }

    protected abstract fun matches(fileOp: FileOp, file: File): Boolean
  }

  companion object {
    val IS_EMPTY: Rule = object : SimpleRule() {
      public override fun matches(fileOp: FileOp, file: File): Boolean {
        return file.name.isEmpty()
      }

      override fun getMessage(file: File, fieldName: String): String {
        return String.format("Please specify a %1\$s.", fieldName)
      }
    }
    val INVALID_SLASHES: Rule = object : SimpleRule() {
      public override fun matches(fileOp: FileOp, file: File): Boolean {
        val path: String = file.path
        return File.separatorChar == '/' && path.contains("\\") || File.separatorChar == '\\' && path.contains("/")
      }

      override fun getMessage(file: File, fieldName: String): String {
        return if (File.separatorChar == '\\') {
          String.format("Your %1\$s contains incorrect slashes ('/').", fieldName)
        }
        else {
          String.format("Your %1\$s contains incorrect slashes ('\\').", fieldName)
        }
      }
    }
    val WHITESPACE: Rule = object : RecursiveRule() {
      public override fun matches(fileOp: FileOp, file: File): Boolean {
        return CharMatcher.whitespace().matchesAnyOf(file.name)
      }

      override fun getMessage(file: File, fieldName: String): String {
        return String.format("%1\$s should not contain whitespace, as this can cause problems with the NDK tools.", fieldName)
      }
    }
    val NON_ASCII_CHARS: Rule = object : RecursiveRule() {
      public override fun matches(fileOp: FileOp, file: File): Boolean {
        return !CharMatcher.ascii().matchesAllOf(file.name)
      }

      override fun getMessage(file: File, fieldName: String): String {
        return String.format("Your %1\$s contains non-ASCII characters.", fieldName)
      }
    }
    val PARENT_DIRECTORY_NOT_WRITABLE: Rule = object : RecursiveRule() {
      override fun matches(fileOp: FileOp, file: File): Boolean {
        val parent: File? = file.parentFile
        return !fileOp.exists(file) && parent != null && fileOp.exists(parent) && !fileOp.canWrite(parent)
      }

      override fun getMessage(file: File, fieldName: String): String {
        return String.format("The path '%1\$s' is not writable. Please choose a new location.", file.parentFile.path)
      }
    }
    val LOCATION_IS_A_FILE: Rule = object : SimpleRule() {
      override fun matches(fileOp: FileOp, file: File): Boolean {
        return fileOp.isFile(file)
      }

      override fun getMessage(file: File, fieldName: String): String {
        return String.format("The %1\$s specified already exists.", fieldName)
      }
    }
    val LOCATION_IS_ROOT: Rule = object : SimpleRule() {
      override fun matches(fileOp: FileOp, file: File): Boolean {
        return file.parent == null
      }

      override fun getMessage(file: File, fieldName: String): String {
        return String.format("The %1\$s cannot be at the filesystem root.", fieldName)
      }
    }
    val PARENT_IS_NOT_A_DIRECTORY: Rule = object : SimpleRule() {
      override fun matches(fileOp: FileOp, file: File): Boolean {
        val parent: File? = file.parentFile
        return parent != null && fileOp.exists(parent) && !fileOp.isDirectory(parent)
      }

      override fun getMessage(file: File, fieldName: String): String {
        return String.format("The %1\$s's parent must be a directory, not a plain file.", fieldName)
      }
    }
    val PATH_NOT_WRITABLE: Rule = object : SimpleRule() {
      override fun matches(fileOp: FileOp, file: File): Boolean {
        return fileOp.exists(file) && !fileOp.canWrite(file)
      }

      override fun getMessage(file: File, fieldName: String): String {
        return String.format("The path '%1\$s' is not writable. Please choose a new location.", file.path)
      }
    }
    val PATH_INSIDE_ANDROID_STUDIO: Rule = object : SimpleRule() {
      override fun matches(fileOp: FileOp, file: File): Boolean {
        val installLocation = PathManager.getHomePathFor(
          Application::class.java)
        return installLocation != null && FileUtil.isAncestor(File(installLocation), file, false)
      }

      override fun getMessage(file: File, fieldName: String): String {
        val applicationName: String? = ApplicationNamesInfo.getInstance().productName
        return String.format("The %1\$s is inside %2\$s's install location.", fieldName, applicationName)
      }
    }
    val NON_EMPTY_DIRECTORY: Rule = object : SimpleRule() {
      override fun matches(fileOp: FileOp, file: File): Boolean {
        return fileOp.listFiles(file).isNotEmpty()
      }

      override fun getMessage(file: File, fieldName: String): String {
        return String.format("'%1s' already exists at the specified %2\$s.", file.name, fieldName)
      }
    }
    /**
     * Note: This should be an error only under Windows (for other platforms should be a warning)
     */
    val ILLEGAL_WINDOWS_FILENAME: Rule = object : RecursiveRule() {
      private val RESERVED_WINDOWS_FILENAMES: Set<String> = ImmutableSet
        .of("con", "prn", "aux", "clock$", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
            "\$mft", "\$mftmirr", "\$logfile", "\$volume", "\$attrdef", "\$bitmap", "\$boot",
            "\$badclus", "\$secure", "\$upcase", "\$extend", "\$quota", "\$objid", "\$reparse")

      public override fun matches(fileOp: FileOp, file: File): Boolean {
        return RESERVED_WINDOWS_FILENAMES.contains(file.name.toLowerCase(Locale.US))
      }

      override fun getMessage(file: File, fieldName: String): String {
        return String.format("Illegal (Windows) filename in %1\$s path: %2\$s.", fieldName, file.name)
      }
    }
    val WINDOWS_PATH_TOO_LONG: Rule = object : SimpleRule() {
      /**
       * Although Android Studio and Gradle support paths longer than MAX_PATH (260) on Windows,
       * there is still a limit to the length of the path to the root directory,
       * as each project directory contains a "gradlew.bat" file (used to build the project),
       * and the length of the path to the "gradlew.bat" cannot exceed MAX_PATH,
       * because Windows limits path of executables to MAX_PATH (for backward compatibility reasons).
       */
      private val WINDOWS_PATH_LENGTH_LIMIT = 240

      override fun matches(fileOp: FileOp, file: File): Boolean {
        return file.absolutePath.length > WINDOWS_PATH_LENGTH_LIMIT
      }

      override fun getMessage(file: File, fieldName: String): String =
        String.format(Locale.US, "The length of the %1\$s exceeds the limit of %2\$d characters.", fieldName, WINDOWS_PATH_LENGTH_LIMIT)
    }
    val ILLEGAL_CHARACTER: Rule = object : RecursiveRule() {
      private val ILLEGAL_CHARACTER_MATCHER: CharMatcher = CharMatcher.anyOf("[/\\\\?%*:|\"<>!;]")

      public override fun matches(fileOp: FileOp, file: File): Boolean {
        return ILLEGAL_CHARACTER_MATCHER.matchesAnyOf(file.name)
      }

      override fun getMessage(file: File, fieldName: String): String {
        val name: String = file.name
        val illegalChar = name[ILLEGAL_CHARACTER_MATCHER.indexIn(name)]
        return String.format("Illegal character in %1\$s path: '%2\$c' in filename %3\$s.", fieldName, illegalChar, name)
      }
    }

    /**
     * A validator that provides reasonable defaults for checking a path's validity.
     *
     * @param pathName name of the path being validated. Used inside [Validator.Result]'s message.
     */
    @JvmStatic
    fun createDefault(pathName: String): PathValidator {
      return Builder().withAllRules(Severity.ERROR).build(pathName)
    }

    private val logger: Logger get() = logger<PathValidator>()
  }
}