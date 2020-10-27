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
package org.jetbrains.android.dom.navigation

import com.android.tools.idea.naveditor.navEditorRuntimePaths
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.ZipUtil
import org.jetbrains.android.AndroidTestCase
import java.io.File

class NavigationSchemaTest3 : AndroidTestCase() {
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("navschematest", "src")

    for (prebuiltPath in navEditorRuntimePaths.keys) {
      val aar = File(prebuiltPath)
      val tempDir = FileUtil.createTempDirectory("NavigationSchemaTest", null)
      ZipUtil.extract(aar, tempDir, null)
      val path = File(tempDir, "classes.jar").path
      LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
      PsiTestUtil.addLibrary(myFixture.module, path)
    }
  }

  fun testNoFragment() {
    NavigationSchema.createIfNecessary(myModule)
  }
}