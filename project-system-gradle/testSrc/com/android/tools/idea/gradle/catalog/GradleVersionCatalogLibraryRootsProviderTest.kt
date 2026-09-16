/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.catalog

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.util.PlatformIcons
import java.io.File
import org.jetbrains.plugins.gradle.service.resolve.GradleVersionCatalogHandler
import org.junit.Rule
import org.junit.Test

class GradleVersionCatalogLibraryRootsProviderTest {

  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  @Test
  fun testProviderReturnsImportedCatalogs() {
    val project = projectRule.project
    val fixture = projectRule.fixture

    // Create a catalog file inside the project (should be ignored)
    val localCatalog = fixture.addFileToProject("gradle/libs.versions.toml", "[libraries]")

    // Create a catalog file outside the project (should be included)
    val externalCatalogFile = File.createTempFile("external", ".toml")
    externalCatalogFile.writeText("[libraries]")
    externalCatalogFile.deleteOnExit()
    val externalCatalog = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(externalCatalogFile)!!

    // Register a test extension that returns both catalogs
    val testExtension =
      object : GradleVersionCatalogHandler {
        override fun getVersionCatalogFiles(module: Module): Map<String, VirtualFile> {
          return mapOf("libs" to localCatalog.virtualFile, "external" to externalCatalog)
        }
      }

    val ep = ExtensionPointName.create<GradleVersionCatalogHandler>("org.jetbrains.plugins.gradle.externallyHandledExtensions")
    ExtensionTestUtil.maskExtensions(ep, listOf(testExtension), projectRule.testRootDisposable)

    val provider = GradleVersionCatalogLibraryRootsProvider()
    val libraries = provider.getAdditionalProjectLibraries(project)

    assertThat(libraries).hasSize(1)
    val library = libraries.first()
    assertThat(library.sourceRoots).containsExactly(externalCatalog)

    assertThat(library).isInstanceOf(ItemPresentation::class.java)
    val presentation = library as ItemPresentation
    assertThat(presentation.presentableText).isEqualTo("Imported Catalog: external")
    assertThat(presentation.getIcon(false)).isEqualTo(PlatformIcons.LIBRARY_ICON)
  }
}
