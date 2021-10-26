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

import com.android.repository.io.FileOpUtils
import com.android.testutils.file.createInMemoryFileSystem
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.file.recordExistingFile
import com.android.testutils.file.someRoot
import com.android.tools.adtui.validation.Validator.Severity
import com.android.tools.idea.ui.validation.validators.PathValidator.Builder
import com.google.common.base.Strings
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import java.util.Locale

class PathValidatorTest {
  @Test
  fun testIsEmptyRuleMatches() {
    assertRuleFails(IS_EMPTY, Paths.get(""))
  }

  @Test
  fun testIsEmptyRuleOk() {
    assertRulePasses(IS_EMPTY, Paths.get("/not/empty.txt"))
  }

  @Test
  fun testInvalidSlashesRuleMatches() {
    // Windows forbids forward- and backslashes in file names, so it isn't possible to get this error on Windows. Furthermore,
    // java.io.File normalizes all the forward slashes to backslashes during construction on Windows, so we can't even test that
    // (impossible) case here.
    Assume.assumeFalse(SystemInfo.isWindows)
    val path = Paths.get("at\\least/one\\of/these\\slashes/are\\wrong")
    assertRuleFails(INVALID_SLASHES, path)
  }

  @Test
  fun testInvalidSlashesRuleOk() {
    val slashedPath = String.format("%1\$ca%1\$cb%1\$cc", File.separatorChar) // /a/b/c or \a\b\c
    assertRulePasses(INVALID_SLASHES, Paths.get(slashedPath))
  }

  @Test
  fun illegalCharacterMatches() {
    val path = Paths.get("bad file name!!!%.txt")
    assertRuleFails(ILLEGAL_CHARACTER, path)
  }

  @Test
  fun illegalCharacterOk() {
    assertRulePasses(ILLEGAL_CHARACTER, Paths.get("/no/illegal/chars").toAbsolutePath())
  }

  @Test
  fun illegalFilenameMatches() {
    val path = Paths.get("/aux/is/reserved/").toAbsolutePath()
    // "aux" is responsible for the reserved keyword failure
    assertRuleFails(ILLEGAL_WINDOWS_FILENAME, path, Paths.get("aux"))
  }

  @Test
  fun illegalFilenameOk() {
    val path = Paths.get("/no/reserved/keywords/")
    assertRulePasses(ILLEGAL_WINDOWS_FILENAME, path)
  }

  @Test
  fun whitespaceMatches() {
    val path = Paths.get("/no whitespace/is allowed/")
    assertRuleFails(WHITESPACE, path)
  }

  @Test
  fun whitespaceOk() {
    val path = Paths.get("/no/whitespace/is/allowed/")
    assertRulePasses(WHITESPACE, path)
  }

  @Test
  fun nonAsciiCharsMatches() {
    val path = Paths.get("/users/\uD83D\uDCA9/")
    assertRuleFails(NON_ASCII_CHARS, path)
  }

  @Test
  fun nonAsciiCharsOk() {
    val path = Paths.get("/users/janedoe/").toAbsolutePath()
    assertRulePasses(NON_ASCII_CHARS, path)
  }

  @Test
  fun parentDirectoryNotWritableMatches() {
    // Can't use in-memory filesystems because jimfs doesn't support permissions completely
    val parent = Files.createTempDirectory("PathValidatorTest-parentDirectoryNotWritableMatches")

    parent.setReadOnly()
    val path = parent.resolve("c/d/e.txt").toAbsolutePath()

    // Because /a/b/ is readonly, it's /a/b/c that finally triggers the failure.
    // This causes the error message to complain about its parent, "a/b/"
    val failureCause = parent.resolve("c").toAbsolutePath()
    assertRuleFails(PARENT_DIRECTORY_NOT_WRITABLE, path, failureCause)
  }

  @Test
  fun parentDirectoryNotWritableOk() {
    val path = createInMemoryFileSystem().someRoot.resolve("a/b/c/d/e.txt")
    assertRulePasses(PARENT_DIRECTORY_NOT_WRITABLE, path)
  }

  @Test
  fun windowsPathTooLongMatches() {
    Assume.assumeTrue(SystemInfo.isWindows)
    val path = Paths.get("c:\\" + Strings.repeat("\\abcdefghi", 24))
    assertRuleFails(WINDOWS_PATH_TOO_LONG, path)
  }

  @Test
  fun pathTooLongOk() {
    Assume.assumeTrue(SystemInfo.isWindows)
    val path = Paths.get("c:\\" + Strings.repeat("\\abcdefghi", 23))
    assertRulePasses(WINDOWS_PATH_TOO_LONG, path)
  }

  @Test
  fun locationIsAFileMatches() {
    val path = createInMemoryFileSystem().someRoot.resolve("a/b/c/d/e.txt")
    path.recordExistingFile()
    assertRuleFails(LOCATION_IS_A_FILE, path)
  }

  @Test
  fun locationIsAFileOk() {
    val parent = createInMemoryFileSystemAndFolder("a/b/c/d")
    parent.resolve("e.txt").recordExistingFile()
    assertRulePasses(LOCATION_IS_A_FILE, parent.resolve("e2.txt"))
  }

  @Test
  fun locationIsRootMatches() {
    assertRuleFails(LOCATION_IS_ROOT, createInMemoryFileSystem().someRoot)
  }

  @Test
  fun locationIsRootOk() {
    assertRulePasses(LOCATION_IS_ROOT, createInMemoryFileSystemAndFolder("not/root"))
  }

  @Test
  fun parentIsNotADirectoryMatches() {
    val f = createInMemoryFileSystem().someRoot.resolve("a/b/c/d/e.txt").recordExistingFile()
    assertRuleFails(PARENT_IS_NOT_A_DIRECTORY, f.resolve("f.txt"))
  }

  @Test
  fun parentIsNotADirectoryOk() {
    val f = createInMemoryFileSystemAndFolder("a/b/c/d/e")
    assertRulePasses(PARENT_IS_NOT_A_DIRECTORY, f.resolve("f.txt"))
  }

  @Test
  fun pathNotWritableMatches() {
    // Can't use in-memory filesystems because jimfs doesn't support permissions completely
    val path = Files.createTempDirectory("PathValidatorTest-pathNotWritableMatches")
    path.setReadOnly()
    assertRuleFails(PATH_NOT_WRITABLE, path)
  }

  @Test
  fun pathNotWritableOk() {
    val path = Paths.get("/a/b/c/d/e/").toAbsolutePath()
    assertRulePasses(PATH_NOT_WRITABLE, path)
  }

  @Test
  fun nonEmptyDirectoryMatches() {
    val path = createInMemoryFileSystemAndFolder("a/b/c/d/")
    path.resolve("e.txt").recordExistingFile()
    assertRuleFails(NON_EMPTY_DIRECTORY, path)
  }

  @Test
  fun nonEmptyDirectoryOk() {
    val path = createInMemoryFileSystemAndFolder("a/b/c/d/")
    assertRulePasses(NON_EMPTY_DIRECTORY, path)
  }

  @Test
  fun errorsShownBeforeWarnings() {
    val validator = Builder().withWarning(WHITESPACE).withError(ILLEGAL_CHARACTER).build("test path")
    // This path validator has its warning registered before its error, but we should still show the error first
    val result = validator.validate(Paths.get("whitespace and illegal characters!%"))
    assertThat(result.severity).isEqualTo(Severity.ERROR)
  }

  @Test
  fun fileNameRule() {
    val allCapRule = filenameRule("all cap") { it == it.toUpperCase(Locale.US) }
    assertRulePasses(allCapRule, Paths.get("foo/bar/ALL_CAP"))
    assertRuleFails(allCapRule, Paths.get("foo/bar/Not_All_Cap"))
  }
}

private fun assertRuleFails(rule: Rule, path: Path) {
  assertRuleFails(rule, path, path) // inputFile is itself the cause of failure
}

private fun assertRuleFails(rule: Rule, inputPath: Path, failureCause: Path) {
  val validator = Builder().withError(rule).build("test path")
  val result = validator.validate(inputPath)
  assertThat(result.severity).isEqualTo(Severity.ERROR)
  assertThat(result.message).isEqualTo(rule.getMessage(failureCause, "test path"))
}

private fun assertRulePasses(rule: Rule, path: Path) {
  val validator = Builder().withError(rule) .build("test path")
  val result = validator.validate(path)
  assertThat(result.severity).isEqualTo(Severity.OK)
}

private fun Path.setReadOnly() {
  if (FileOpUtils.isWindows()) {
    val acls = Files.getFileAttributeView(this, AclFileAttributeView::class.java)
    val newAcls = acls.acl.map {
      AclEntry.newBuilder(it).setPermissions(it.permissions().minus(AclEntryPermission.WRITE_DATA)).build()
    }
    acls.acl = newAcls

    Files.getFileAttributeView(this, DosFileAttributeView::class.java).setReadOnly(true)
  }
  else {
    val permissions: MutableSet<PosixFilePermission> = EnumSet.copyOf(Files.getPosixFilePermissions(this))
    permissions.remove(PosixFilePermission.OWNER_WRITE)
    permissions.remove(PosixFilePermission.GROUP_WRITE)
    permissions.remove(PosixFilePermission.OTHERS_WRITE)
    Files.setPosixFilePermissions(this, permissions)
  }
}