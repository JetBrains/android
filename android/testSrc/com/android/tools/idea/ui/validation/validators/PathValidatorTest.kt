/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.repository.testframework.MockFileOp
import com.android.tools.adtui.validation.Validator.Severity
import com.android.tools.idea.ui.validation.validators.PathValidator.Builder
import com.android.tools.idea.ui.validation.validators.PathValidator.Rule
import com.google.common.base.Strings
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File

class PathValidatorTest {
  private var fileOp: MockFileOp? = null
  @Before
  fun setUp() {
    fileOp = MockFileOp()
  }

  @Test
  fun testIsEmptyRuleMatches() {
    assertRuleFails(fileOp, PathValidator.IS_EMPTY, File(""))
  }

  @Test
  fun testIsEmptyRuleOk() {
    assertRulePasses(fileOp, PathValidator.IS_EMPTY, File("/not/empty.txt"))
  }

  @Test
  fun testInvalidSlashesRuleMatches() {
    // java.io.File normalizes all the forward slashes to backslashes during construction on Windows, so it becomes virtually impossible
    // to pass a File with an "invalid" slash. Note that this behavior isn't symmetric to Linux/MacOSX,
    // where backslashes remain "as is", despite being not supported as path separators
    Assume.assumeFalse(SystemInfo.isWindows)
    val file = File("at\\least/one\\of/these\\slashes/are\\wrong")
    assertRuleFails(fileOp, PathValidator.INVALID_SLASHES, file)
  }

  @Test
  fun testInvalidSlashesRuleOk() {
    val slashedPath = String.format("%1\$ca%1\$cb%1\$cc", File.separatorChar) // /a/b/c or \a\b\c
    assertRulePasses(fileOp, PathValidator.INVALID_SLASHES, File(slashedPath))
  }

  @Test
  fun illegalCharacterMatches() {
    val file = File("\"bad file name\"!!!.txt")
    assertRuleFails(fileOp, PathValidator.ILLEGAL_CHARACTER, file)
  }

  @Test
  fun illegalCharacterOk() {
    assertRulePasses(fileOp, PathValidator.ILLEGAL_CHARACTER, File("/no/illegal/chars"))
  }

  @Test
  fun illegalFilenameMatches() {
    val file = File("/aux/is/reserved/")
    // "aux" is responsible for the reserved keyword failure
    assertRuleFails(fileOp, PathValidator.ILLEGAL_WINDOWS_FILENAME, file, File("aux"))
  }

  @Test
  fun illegalFilenameOk() {
    val file = File("/no/reserved/keywords/")
    assertRulePasses(fileOp, PathValidator.ILLEGAL_WINDOWS_FILENAME, file)
  }

  @Test
  fun whitespaceMatches() {
    val file = File("/no whitespace/is allowed/")
    assertRuleFails(fileOp, PathValidator.WHITESPACE, file)
  }

  @Test
  fun whitespaceOk() {
    val file = File("/no/whitespace/is/allowed/")
    assertRulePasses(fileOp, PathValidator.WHITESPACE, file)
  }

  @Test
  fun nonAsciiCharsMatches() {
    val file = File("/users/\uD83D\uDCA9/")
    assertRuleFails(fileOp, PathValidator.NON_ASCII_CHARS, file)
  }

  @Test
  fun nonAsciiCharsOk() {
    val file = File("/users/janedoe/")
    assertRulePasses(fileOp, PathValidator.NON_ASCII_CHARS, file)
  }

  @Test
  fun parentDirectoryNotWritableMatches() {
    val parent = File("/a/b/")
    fileOp!!.mkdirs(parent)
    fileOp!!.setReadOnly(parent)
    val file = File("/a/b/c/d/e.txt")

    // Because /a/b/ is readonly, it's /a/b/c that finally triggers the failure.
    // This causes the error message to complain about its parent, "a/b/"
    val failureCause = File("/a/b/c")
    assertRuleFails(fileOp, PathValidator.PARENT_DIRECTORY_NOT_WRITABLE, file, failureCause)
  }

  @Test
  fun parentDirectoryNotWritableOk() {
    val file = File("/a/b/c/d/e.txt")
    assertRulePasses(fileOp, PathValidator.PARENT_DIRECTORY_NOT_WRITABLE, file)
  }

  @Test
  fun windowsPathTooLongMatches() {
    Assume.assumeTrue(SystemInfo.isWindows)
    val file = File("c:\\" + Strings.repeat("\\abcdefghi", 24))
    assertRuleFails(fileOp, PathValidator.WINDOWS_PATH_TOO_LONG, file)
  }

  @Test
  fun pathTooLongOk() {
    Assume.assumeTrue(SystemInfo.isWindows)
    val file = File("c:\\" + Strings.repeat("\\abcdefghi", 23))
    assertRulePasses(fileOp, PathValidator.WINDOWS_PATH_TOO_LONG, file)
  }

  @Test
  fun locationIsAFileMatches() {
    val file = File("/a/b/c/d/e.txt")
    fileOp!!.createNewFile(file)
    assertRuleFails(fileOp, PathValidator.LOCATION_IS_A_FILE, file)
  }

  @Test
  fun locationIsAFileOk() {
    fileOp!!.createNewFile(File("/a/b/c/d/e.txt"))
    val file = File("/a/b/c/d/e2.txt")
    assertRulePasses(fileOp, PathValidator.LOCATION_IS_A_FILE, file)
  }

  @Test
  fun locationIsRootMatches() {
    assertRuleFails(fileOp, PathValidator.LOCATION_IS_ROOT, File("/"))
  }

  @Test
  fun locationIsRootOk() {
    assertRulePasses(fileOp, PathValidator.LOCATION_IS_ROOT, File("/not/root"))
  }

  @Test
  fun parentIsNotADirectoryMatches() {
    fileOp!!.createNewFile(File("/a/b/c/d/e.txt"))
    val file = File("/a/b/c/d/e.txt/f.txt")
    assertRuleFails(fileOp, PathValidator.PARENT_IS_NOT_A_DIRECTORY, file)
  }

  @Test
  fun parentIsNotADirectoryOk() {
    fileOp!!.recordExistingFolder(File("/a/b/c/d/e/"))
    val file = File("/a/b/c/d/e/f.txt")
    assertRulePasses(fileOp, PathValidator.PARENT_IS_NOT_A_DIRECTORY, file)
  }

  @Test
  fun pathNotWritableMatches() {
    val file = File("/a/b/c/d/e/")
    fileOp!!.recordExistingFolder(file)
    fileOp!!.setReadOnly(file)
    assertRuleFails(fileOp, PathValidator.PATH_NOT_WRITABLE, file)
  }

  @Test
  fun pathNotWritableOk() {
    val file = File("/a/b/c/d/e/")
    assertRulePasses(fileOp, PathValidator.PATH_NOT_WRITABLE, file)
  }

  @Test
  fun nonEmptyDirectoryMatches() {
    fileOp!!.createNewFile(File("/a/b/c/d/e.txt"))
    val file = File("/a/b/c/d/")
    assertRuleFails(fileOp, PathValidator.NON_EMPTY_DIRECTORY, file)
  }

  @Test
  fun nonEmptyDirectoryOk() {
    val file = File("/a/b/c/d/")
    fileOp!!.recordExistingFolder(file)
    assertRulePasses(fileOp, PathValidator.NON_EMPTY_DIRECTORY, file)
  }

  @Test
  fun errorsShownBeforeWarnings() {
    val validator = Builder()
      .withRule(PathValidator.WHITESPACE, Severity.WARNING)
      .withRule(PathValidator.ILLEGAL_CHARACTER, Severity.ERROR)
      .build("test path")

    // This path validator has its warning registered before its error, but we should still show the error first
    val result = validator.validate(File("whitespace and illegal characters??!"))
    assertThat(result.severity).isEqualTo(Severity.ERROR)
  }

  @Test(expected = java.lang.IllegalArgumentException::class)
  fun ruleMustHaveValidSeverity() {
    Builder().withRule(PathValidator.ILLEGAL_CHARACTER, Severity.OK)
  }
}

private fun assertRuleFails(fileOp: FileOp?, rule: Rule?, file: File?) {
  assertRuleFails(fileOp, rule, file, file) // inputFile is itself the cause of failure
}

private fun assertRuleFails(fileOp: FileOp?, rule: Rule?, inputFile: File?, failureCause: File?) {
  val validator = Builder().withRule(rule!!, Severity.ERROR).build("test path", fileOp!!)
  val result = validator.validate(inputFile!!)
  assertThat(result.severity).isEqualTo(Severity.ERROR)
  assertThat(result.message).isEqualTo(rule.getMessage(failureCause!!, "test path"))
}

private fun assertRulePasses(fileOp: FileOp?, rule: Rule?, file: File?) {
  val validator = Builder().withRule(rule!!, Severity.ERROR).build("test path", fileOp!!)
  val result = validator.validate(file!!)
  assertThat(result.severity).isEqualTo(Severity.OK)
}
