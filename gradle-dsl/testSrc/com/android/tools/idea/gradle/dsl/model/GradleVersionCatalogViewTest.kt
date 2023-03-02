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
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogView
import junit.framework.Assert
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File
import java.io.IOException

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
    Assert.assertNotNull(catalogToFile["libs"])
    Assert.assertNotNull(catalogToFile["libs"]!!.path.endsWith("gradle/libs.versions.toml"))
    Assert.assertNotNull(catalogToFile["foo"])
    Assert.assertNotNull(catalogToFile["foo"]!!.path.endsWith("gradle/foo.versions.toml"))
  }

  @Test
  @Throws(IOException::class)
  fun testParseVersionCatalogsFilterNonExistingFiles() {
    writeToSettingsFile(TestFile.PARSE_VERSION_CATALOGS_FOR_VIEW)
    val settingsModel = gradleSettingsModel
    val view = getVersionCatalogView(settingsModel)
    val catalogToFile = view.catalogToFileMap
    assertSize(1, catalogToFile.entries)
    Assert.assertNotNull(catalogToFile["libs"])
    Assert.assertNotNull(catalogToFile["libs"]!!.path.endsWith("gradle/libs.versions.toml"))
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
    Assert.assertNotNull(catalogToFile["libs"])
    Assert.assertNotNull(catalogToFile["libs"]!!.path.endsWith("gradle/libs.versions.toml"))
  }

  @Test
  @Throws(IOException::class)
  fun testAddVersionCatalogs() {
    writeToSettingsFile("""
      dependencyResolutionManagement {
      }
    """)

    val settingsModel = gradleSettingsModel
    var view = getVersionCatalogView(settingsModel)
    var catalogToFile = view.catalogToFileMap
    assertSize(1, catalogToFile.entries)
    Assert.assertNotNull(catalogToFile["libs"])

    val dependencyResolutionManagementModel = settingsModel.dependencyResolutionManagement()
    val foo = dependencyResolutionManagementModel.addVersionCatalog("foo")
    foo.from().setValue("gradle/foo.versions.toml")
    createCatalogFile("foo.versions.toml")
    applyChanges(settingsModel)

    catalogToFile = view.catalogToFileMap // getting updated version

    assertSize(2, catalogToFile.entries)
    Assert.assertNotNull(catalogToFile["foo"])
    Assert.assertNotNull(catalogToFile["foo"]!!.path.endsWith("gradle/foo.versions.toml"))
  }

  private fun getVersionCatalogView(settingsModel: GradleSettingsModel): GradleVersionCatalogView =
    GradleVersionCatalogViewImpl(settingsModel)

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    PARSE_VERSION_CATALOGS_FOR_VIEW("parseVersionCatalogsForView");

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/gradleCatalogView/$path", extension)
    }
  }
}