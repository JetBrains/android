/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipFile
import junit.framework.TestCase

private const val NAME_FORMAT = "ZipUtilTest/file%d.txt"
private const val TEXT_FORMAT = "This is file %d."
private const val FILE_COUNT = 3

class ZipUtilTest : TestCase() {
  lateinit var testDirectoryPath: Path

  override fun setUp() {
    super.setUp()
    testDirectoryPath = FileUtil.createTempDirectory("ZipUtilTest", null).toPath()
  }

  override fun tearDown() {
    FileUtils.deleteRecursivelyIfExists(testDirectoryPath.toFile())
    super.tearDown()
  }

  fun testZipFiles() {
    val list = mutableListOf<ZipData>()
    for (i in 1..FILE_COUNT) {
      val filePath = testDirectoryPath.resolve("file$i.txt").toString()
      File(filePath).writeText(TEXT_FORMAT.format(i))
      val data = ZipData(filePath, NAME_FORMAT.format(i))
      list.add(data)
    }

    val archive = testDirectoryPath.resolve("archive.zip").toString()
    var count = 0
    val data = mutableListOf<ZipData>()
    zipFiles(list, archive) {
      count++
      data.add(it)
    }

    ZipFile(archive).use {
      assertThat(it.entries().toList().count()).isEqualTo(FILE_COUNT)

      var i = 0
      for (entry in it.entries()) {
        i++
        assertThat(entry.name).isEqualTo(NAME_FORMAT.format(i))
        val input = it.getInputStream(entry)
        val bytes = String(input.readBytes())
        assertThat(bytes).isEqualTo(TEXT_FORMAT.format(i))
      }
    }

    assertThat(count).isEqualTo(FILE_COUNT)
    for (i in 1..FILE_COUNT) {
      assertThat(data[i - 1].path).isEqualTo(testDirectoryPath.resolve("file$i.txt").toString())
    }
  }
}
