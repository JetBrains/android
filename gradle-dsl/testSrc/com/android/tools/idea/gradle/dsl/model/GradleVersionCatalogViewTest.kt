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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.api.GradleModelProvider
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogView
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File
import java.io.IOException
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_COMPOSITE_SINGLE
import junit.framework.TestCase

class GradleVersionCatalogViewTest: GradleFileModelTestCase() {
  @Test
  @Throws(IOException::class)
  fun testParseVersionCatalogs() {
    writeToSettingsFile(TestFile.PARSE_VERSION_CATALOGS_FOR_VIEW)
    createCatalogFile("foo.versions.toml")
    val settingsModel = gradleSettingsModel
    val view = getVersionCatalogView(settingsModel)
    val catalogToFile = view.catalogToFileMap
    assertSize(2, catalogToFile.entries)
    assertThat(catalogToFile["libs"]).isNotNull()
    assertThat(catalogToFile["libs"]!!.path).endsWith("gradle/libs.versions.toml")
    assertThat(catalogToFile["foo"]).isNotNull()
    assertThat(catalogToFile["foo"]!!.path).endsWith("gradle/foo.versions.toml")
  }

  @Test
  @Throws(IOException::class)
  fun testParseVersionCatalogsFilterNonExistingFiles() {
    writeToSettingsFile(TestFile.PARSE_VERSION_CATALOGS_FOR_VIEW)
    val settingsModel = gradleSettingsModel
    val view = getVersionCatalogView(settingsModel)
    val catalogToFile = view.catalogToFileMap
    assertSize(1, catalogToFile.entries)
    assertThat(catalogToFile["libs"]).isNotNull()
    assertThat(catalogToFile["libs"]!!.path).endsWith("gradle/libs.versions.toml")
  }

  @Test
  @Throws(IOException::class)
  fun returnAtLeastDefaultCatalogIfFileIsThere() {
    writeToSettingsFile("")
    writeToVersionCatalogFile("")
    val settingsModel = gradleSettingsModel
    val view = getVersionCatalogView(settingsModel)
    val catalogToFile = view.catalogToFileMap
    assertSize(1, catalogToFile.entries)
    assertThat(catalogToFile["libs"]).isNotNull()
    assertThat(catalogToFile["libs"]!!.path).endsWith("gradle/libs.versions.toml")
  }

  @Test
  fun compositeBuildTest(){
    writeToSettingsFile("")
    writeToVersionCatalogFile("")
    runWriteAction<Unit, IOException> {
      var compositeRoot = myProjectBasePath.createChildDirectory(this, "CompositeBuild")
      assertTrue(compositeRoot.exists())
      createFileAndWriteContent(compositeRoot.createChildData(this, "settings$myTestDataExtension"), TestFile.PARSE_VERSION_CATALOGS_FOR_VIEW)
      createFileAndWriteContent(compositeRoot.createChildData(this, "build$myTestDataExtension"), COMPOSITE_BUILD_COMPOSITE_SINGLE)
      var gradle = compositeRoot.createChildDirectory(this, "gradle")
      assertTrue(gradle.exists())
      saveFileUnderWrite(gradle.createChildData(this, "foo.versions.toml"), """
        [libraries]
      """.trimIndent())
    }
    val buildPath = File(project.basePath, "CompositeBuild")
    val view = GradleModelProvider.getInstance().getVersionCatalogView(project, buildPath.path)
    TestCase.assertNotNull(view)
    assertThat(view!!.getCatalogToFileMap()).hasSize(1)
    val defaultLib = view.getCatalogToFileMap()["foo"]
    assertThat(defaultLib).isNotNull()
    assertThat(defaultLib!!.path).endsWith("CompositeBuild/gradle/foo.versions.toml")
  }

  @Test
  fun compositeBuildTestNoSettings(){
    writeToSettingsFile("")
    writeToVersionCatalogFile("")
    var compositeRoot: VirtualFile
    var gradle: VirtualFile
    runWriteAction<Unit, IOException> {
      compositeRoot = myProjectBasePath.createChildDirectory(this, "CompositeBuild")
      assertTrue(compositeRoot.exists())
      // no settings
      createFileAndWriteContent(compositeRoot.createChildData(this, "build$myTestDataExtension"), COMPOSITE_BUILD_COMPOSITE_SINGLE)
      gradle = compositeRoot.createChildDirectory(this, "gradle")
      assertTrue(gradle.exists())
      saveFileUnderWrite(gradle.createChildData(this, "libs.versions.toml"), """
        [libraries]
      """.trimIndent())
    }
    val buildPath = File(project.basePath, "CompositeBuild")
    val view = GradleModelProvider.getInstance().getVersionCatalogView(project, buildPath.path)
    TestCase.assertNotNull(view)
    assertThat(view!!.getCatalogToFileMap()).hasSize(1)
    val defaultLib = view.getCatalogToFileMap()["libs"]
    assertThat(defaultLib).isNotNull()
    assertThat(defaultLib!!.path).endsWith("CompositeBuild/gradle/libs.versions.toml")
  }


  @Test
  @Throws(IOException::class)
  fun testAddVersionCatalogs() {
    writeToSettingsFile("""
      dependencyResolutionManagement {
      }
    """)

    val settingsModel = gradleSettingsModel
    val view = getVersionCatalogView(settingsModel)
    var catalogToFile = view.catalogToFileMap
    assertSize(1, catalogToFile.entries)
    assertThat(catalogToFile["libs"]).isNotNull()

    val dependencyResolutionManagementModel = settingsModel.dependencyResolutionManagement()
    val foo = dependencyResolutionManagementModel.addVersionCatalog("foo")
    foo.from().setValue("gradle/foo.versions.toml")
    createCatalogFile("foo.versions.toml")
    applyChanges(settingsModel)

    catalogToFile = view.catalogToFileMap // getting updated version

    assertSize(2, catalogToFile.entries)
    assertThat(catalogToFile["foo"]).isNotNull()
    assertThat(catalogToFile["foo"]!!.path).endsWith("gradle/foo.versions.toml")
  }

  @Test
  @Throws(IOException::class)
  fun testDefaultVersionCatalogWithNoSettingsFile() {
    removeSettingsFile()
    createCatalogFile("libs.versions.toml")
    val view = GradleModelProvider.getInstance().getVersionCatalogView(project)
    assertThat(view.getCatalogToFileMap()).hasSize(1)
    val defaultLib = view.getCatalogToFileMap()["libs"]
    assertThat(defaultLib).isNotNull()
    assertThat(defaultLib!!.path).endsWith("gradle/libs.versions.toml")
  }

  private fun getVersionCatalogView(settingsModel: GradleSettingsModel): GradleVersionCatalogView =
    GradleVersionCatalogViewImpl(settingsModel)

  private fun createFileAndWriteContent(file: VirtualFile, content: TestFileName) {
    assertTrue(file.exists())
    prepareAndInjectInformationForTest(content, file)
  }

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    PARSE_VERSION_CATALOGS_FOR_VIEW("parseVersionCatalogsForView");

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/gradleCatalogView/$path", extension)
    }
  }
}