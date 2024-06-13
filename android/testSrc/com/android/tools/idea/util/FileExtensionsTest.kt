/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.util

import com.android.test.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.idea.apk.viewer.ApkFileSystem
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.util.io.URLUtil
import org.jetbrains.android.AndroidTestCase
import java.io.File

class FileExtensionsTest : AndroidTestCase() {
  fun testRegularFile() {
    val ioFile = File(getTestDataPath()).resolve("AndroidManifest.xml").canonicalFile
    assertTrue(ioFile.exists())
    val vfsFile = ioFile.toVirtualFile(refresh = true)!!

    checkPathStringFromVirtualFile(vfsFile)

    assertEquals(ioFile, vfsFile.toIoFile())
    assertEquals(vfsFile, ioFile.toVirtualFile())
    assertEquals(ioFile, vfsFile.toPathString().toFile())
  }

  fun testApk() {
    val apkFile = TestUtils.resolveWorkspacePath("testData/aar/design_aar/res.apk")
    assertThat(apkFile).exists()
    val entryPath = FileUtilRt.toSystemIndependentName(apkFile.toString()) + ApkFileSystem.APK_SEPARATOR +
                    "res/drawable-mdpi-v4/design_ic_visibility.png"
    val apkFsUrl = ApkFileSystem.PROTOCOL + "://" + entryPath

    val vfsFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(apkFsUrl)!!
    checkPathStringFromVirtualFile(vfsFile)

    val pathString = vfsFile.toPathString()
    assertThat(pathString.filesystemUri.scheme).isEqualTo(ApkFileSystem.PROTOCOL)
    assertThat(pathString.rawPath).isEqualTo(entryPath)
  }

  fun testTemp() {
    val vfsFile = runWriteAction {
      TempFileSystem.getInstance()
        .findFileByPath("/")!!
        .createChildData(this, FileExtensionsTest::class.qualifiedName!!)
        .apply { setBinaryContent("hello".toByteArray()) }
    }

    checkPathStringFromVirtualFile(vfsFile)
  }

  private fun checkPathStringFromVirtualFile(vfsFile: VirtualFile) {
    assertTrue(vfsFile.exists())

    // VFS uses three slashes in its URLs (two on Windows) and won't recognize URLs like `file:/foo` or `temp:/foo`. Make sure we're compatible with that.
    val urlRootSlashes = if (SystemInfo.isWindows) "://" else ":///"

    assertTrue(vfsFile.url.contains(urlRootSlashes))

    val pathString = vfsFile.toPathString()
    val pathRegex = if (SystemInfo.isWindows && pathString.filesystemUri.scheme.startsWith(URLUtil.FILE_PROTOCOL)) "/[A-Z]:/" else "/"
    assertThat(pathString.filesystemUri.path).matches(pathRegex)

    assertEquals(vfsFile, pathString.toVirtualFile())
    assertEquals(vfsFile, VirtualFileManager.getInstance().refreshAndFindFileByUrl(pathString.toString()))
  }
}
