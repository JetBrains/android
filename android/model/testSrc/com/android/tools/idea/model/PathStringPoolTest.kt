// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.model

import com.android.ide.common.util.PathString
import com.android.tools.idea.util.toPathString
import com.android.tools.idea.util.toVirtualFile
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.testFramework.HeavyPlatformTestCase
import org.junit.Test
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.Files

class PathStringPoolTest : HeavyPlatformTestCase() {

  lateinit var fs: FileSystem

  lateinit var aFile: PathString
  lateinit var aVirtualFile: PathString
  lateinit var aPath: PathString

  val TEST_STRING = "This is a test"

  override fun setUp() {
    super.setUp()
    val byteArray = TEST_STRING.toByteArray(Charsets.UTF_8)
    // Set up a PathString to something on the filesystem
    val someFile = File.createTempFile("PathStringPoolTest", ".tmp")
    Files.write(someFile.toPath(), byteArray)
    aFile = PathString(someFile)

    // Set up a PathString to something in an in-memory nio filesystem that can't exist on disk
    fs = Jimfs.newFileSystem(Configuration.unix())
    val somePath = fs.getPath("tempFile")
    Files.write(somePath, byteArray)
    aPath = PathString(somePath)

    // Set up a PathString to a VirtualFile in a TemporaryFileSystem that can't exist on disk
    val vfs = TempFileSystem.getInstance()

    val root = VirtualFileManager.getInstance().findFileByUrl("temp:///")
    var child: VirtualFile? = null
    ApplicationManager.getApplication().runWriteAction {
      child = root!!.createChildData(Any(), "tempFile.txt")
      child!!.setBinaryContent(byteArray)
    }
    aVirtualFile = child!!.toPathString()
  }

  override fun tearDown() {
    try {
      aFile.toFile()?.delete()
      fs.close()
      TempFileSystem.getInstance().deleteFile(Any(), aVirtualFile.toVirtualFile()!!)
    } catch (t: Throwable) {
      addSuppressedException(t)
    } finally {
      super.tearDown()
    }
  }

  fun verifyFileContents(f: File) {
    assertThat(String(Files.readAllBytes(f.toPath()), Charsets.UTF_8)).isEqualTo(TEST_STRING)
  }

  @Test
  fun testToFile() {
    PathStringPool().use {
      verifyFileContents(it.toFile(aFile))
      verifyFileContents(it.toFile(aVirtualFile))
      verifyFileContents(it.toFile(aPath))
    }
  }
}
