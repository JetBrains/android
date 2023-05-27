/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.sdk

import com.android.testutils.file.createInMemoryFileSystem
import com.android.testutils.file.someRoot
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import java.util.stream.Collectors

class NdkPathsTest : TestCase() {
  private var tmpDir: File? = null

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    tmpDir = null
  }

  @Throws(Exception::class)
  override fun tearDown() {
    tmpDir?.let { FileUtil.delete(it) }
  }

  @Throws(java.lang.Exception::class)
  fun testInvalidNdkDirectory() {
    val mockFile = createFileMock()
    var result = validateAndroidNdk(mockFile, false)
    assertFalse(result.success)
    assertEquals("The NDK path does not belong to a directory.", result.message)
    result = validateAndroidNdk(mockFile, true)
    assertFalse(result.success)
    assertEquals(String.format("The NDK path\n'%1\$s'\ndoes not belong to a directory.", mockFile), result.message)
  }

  @Throws(java.lang.Exception::class)
  fun testUnReadableNdkDirectory() {
    val mockFile = Files.createTempDirectory("NdkPathsTest-testUnReadableNdkDirectory")
    setUnreadable(mockFile)
    var result = validateAndroidNdk(mockFile, false)
    assertFalse(result.success)
    assertEquals("The NDK path is not readable.", result.message)
    result = validateAndroidNdk(mockFile, true)
    assertFalse(result.success)
    assertEquals(String.format("The NDK path\n'%1\$s'\nis not readable.", mockFile), result.message)
  }

  @Throws(java.lang.Exception::class)
  fun testNoPlatformsNdkDirectory() {
    tmpDir = FileUtil.createTempDirectory(NdkPathsTest::class.java.simpleName, "testNoPlatformsNdkDirectory")
    var result = validateAndroidNdk(tmpDir, false)
    assertFalse(result.success)
    assertEquals("NDK does not contain any platforms.", result.message)
    result = validateAndroidNdk(tmpDir, true)
    assertFalse(result.success)
    assertEquals(String.format("The NDK at\n'%1\$s'\ndoes not contain any platforms.", tmpDir!!.path), result.message)
  }

  @Throws(java.lang.Exception::class)
  fun testNoToolchainsNdkDirectory() {
    tmpDir = FileUtil.createTempDirectory(NdkPathsTest::class.java.simpleName, "testNoToolchainsNdkDirectory")
    FileUtil.createDirectory(File(tmpDir, "platforms"))
    var result = validateAndroidNdk(tmpDir, false)
    assertFalse(result.success)
    assertEquals("NDK does not contain any toolchains.", result.message)
    result = validateAndroidNdk(tmpDir, true)
    assertFalse(result.success)
    assertEquals(String.format("The NDK at\n'%1\$s'\ndoes not contain any toolchains.", tmpDir!!.path), result.message)
  }

  @Throws(java.lang.Exception::class)
  fun testValidNdkDirectory() {
    tmpDir = FileUtil.createTempDirectory(NdkPathsTest::class.java.name, "testValidNdkDirectory")
    FileUtil.createDirectory(File(tmpDir, "platforms"))
    FileUtil.createDirectory(File(tmpDir, "toolchains"))
    var result = validateAndroidNdk(tmpDir, false)
    assertTrue(result.message, result.success)
    result = validateAndroidNdk(tmpDir, true)
    assertTrue(result.message, result.success)
  }

  private fun createFileMock(): Path =
      createInMemoryFileSystem().someRoot.resolve("dummy/path".replace('/', File.separatorChar))

  @Throws(java.lang.Exception::class)
  private fun setUnreadable(path: Path) {
    if (SystemInfo.isWindows) {
      val acls = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
      val newAcls = acls.acl.stream()
        .map { acl: AclEntry? ->
          AclEntry.newBuilder(acl).setPermissions(EnumSet.noneOf(
            AclEntryPermission::class.java)).build()
        }
        .collect(Collectors.toList())
      acls.acl = newAcls
    }
    else {
      Files.setPosixFilePermissions(path, EnumSet.noneOf(PosixFilePermission::class.java))
    }
  }
}