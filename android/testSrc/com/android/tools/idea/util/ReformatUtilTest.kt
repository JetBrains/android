/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class ReformatUtilTest : BasePlatformTestCase() {

  fun testReformatRearrangeAndSave_MultipleFilesPerFileWriteAction() {
    val unformattedJava = "package com.example;\npublic class MyClass {\npublic void method() {}\n}"
    val unformattedXml = "<root   attr2=\"b\"   attr1=\"a\"  />"

    val tempDir = FileUtil.createTempDirectory("reformat_test", null)
    try {
      val javaFile = File(tempDir, "MyClass.java").apply { writeText(unformattedJava) }
      val xmlFile = File(tempDir, "test.xml").apply { writeText(unformattedXml) }

      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir)

      ReformatUtil.reformatRearrangeAndSave(project, listOf(javaFile, xmlFile))

      assertTrue(javaFile.readText().contains("    public void method()"))
      val xmlContent = xmlFile.readText()
      assertTrue(xmlContent.contains("attr1=\"a\"") && xmlContent.contains("attr2=\"b\""))
    } finally {
      FileUtil.delete(tempDir)
    }
  }
}
