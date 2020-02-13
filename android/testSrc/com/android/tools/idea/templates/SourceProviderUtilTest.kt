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
package com.android.tools.idea.templates

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import org.jetbrains.android.facet.SourceProviderManager

class SourceProviderUtilTest : AndroidGradleTestCase() {
  fun testSourceProviderIsContainedByFolder() {
    loadProject(PROJECT_WITH_APPAND_LIB, "app")

    val paidFlavorSourceProvider = SourceProviderManager.getInstance(myAndroidFacet)
      .currentAndSomeFrequentlyUsedInactiveSourceProviders
      .filter { it -> it.name.equals("paid", ignoreCase = true) }
      .single()

    val moduleFile = findFileByIoFile(projectFolderPath, true)!!.findFileByRelativePath("app")
    assertNotNull(moduleFile)
    val javaSrcFile = moduleFile!!.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid")!!
    assertNotNull(javaSrcFile)

    assertFalse(paidFlavorSourceProvider.isContainedBy(javaSrcFile))

    val flavorRoot = moduleFile.findFileByRelativePath("src/paid")!!
    assertNotNull(flavorRoot)

    assertTrue(paidFlavorSourceProvider.isContainedBy(flavorRoot))

    val srcFile = moduleFile.findChild("src")!!
    assertNotNull(srcFile)

    assertTrue(paidFlavorSourceProvider.isContainedBy(srcFile))
  }

  fun testSourceProviderIsContainedByFolder_noSources() {
    loadProject(PROJECT_WITH_APPAND_LIB, "app")

    val paidFlavorSourceProvider = SourceProviderManager.getInstance(myAndroidFacet)
      .currentAndSomeFrequentlyUsedInactiveSourceProviders.single { it.name.equals("basicDebug", ignoreCase = true) }

    val moduleFile = findFileByIoFile(projectFolderPath, true)!!.findFileByRelativePath("app")
    assertNotNull(moduleFile)


    val srcFile = moduleFile!!.findChild("src")!!
    assertNotNull(srcFile)

    assertTrue(paidFlavorSourceProvider.isContainedBy(srcFile))
  }

}