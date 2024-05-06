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
package com.android.tools.idea.gradle.dsl.model.catalog

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File

class GradleCatalogVersionsTest : GradleFileModelTestCase() {

  @Test
  fun testAllDependencies() {
    writeToBuildFile("")
    writeToVersionCatalogFile(TestFile.GET_ALL_DECLARATIONS)

    val catalogModel = projectBuildModel.versionCatalogsModel

    val versions = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations().getAll()
    assertSize(9, versions.toList())
   run {
      val version = versions.toList()[0].second
      assertThat(version.getSpec().compactNotation(), equalTo("1.5.0"))
      assertThat(version.getSpec().getRequire(), equalTo("1.5.0"))
      assertThat(version.getSpec().getStrictly(), nullValue())
      assertThat(version.getSpec().getPrefer(), nullValue())
      assertThat(versions.toList()[0].first, equalTo("literalVersion"))
    }
    run {
      val version = versions.toList()[1].second
      assertThat(version.getSpec().compactNotation(), equalTo("[1.0.0,2.1.0]!!2.0.1"))
      assertThat(version.getSpec().getRequire(), nullValue())
      assertThat(version.getSpec().getStrictly(), equalTo("[1.0.0,2.1.0]"))
      assertThat(version.getSpec().getPrefer(), equalTo("2.0.1"))
      assertThat(versions.toList()[1].first, equalTo("literalVersion2"))
    }
    run {
      val version = versions.toList()[2].second
      assertThat(version.getSpec().compactNotation(), equalTo("1.0.0!!"))
      assertThat(version.getSpec().getRequire(), nullValue())
      assertThat(version.getSpec().getStrictly(), equalTo("1.0.0"))
      assertThat(version.getSpec().getPrefer(), nullValue())
      assertThat(versions.toList()[2].first, equalTo("literalVersion3"))
    }

    run {
      val version = versions.toList()[3].second
      assertThat(version.getSpec().compactNotation(), equalTo("[1.0.0,2.1.0]!!2.0.1"))
      assertThat(version.getSpec().getRequire(), nullValue())
      assertThat(version.getSpec().getStrictly(), equalTo("[1.0.0,2.1.0]"))
      assertThat(version.getSpec().getPrefer(), equalTo("2.0.1"))
      assertThat(versions.toList()[3].first, equalTo("mapVersion"))
    }

    run {
      val version = versions.toList()[4].second
      assertThat(version.getSpec().compactNotation(), equalTo("2.0.1"))
      assertThat(version.getSpec().getRequire(), equalTo("2.0.1"))
      assertThat(version.getSpec().getStrictly(), nullValue())
      assertThat(version.getSpec().getPrefer(), nullValue())
      assertThat(versions.toList()[4].first, equalTo("mapVersion2"))
    }

    run {
      val version = versions.toList()[5].second
      assertThat(version.getSpec().compactNotation(), equalTo("+!!2.0.1"))
      assertThat(version.getSpec().getRequire(), nullValue())
      assertThat(version.getSpec().getStrictly(), nullValue())
      assertThat(version.getSpec().getPrefer(), equalTo("2.0.1"))
      assertThat(versions.toList()[5].first, equalTo("mapVersion3"))
    }

    run {
      val version = versions.toList()[6].second
      assertThat(version.getSpec().compactNotation(), nullValue())
      assertThat(version.getSpec().getRequire(), nullValue())
      assertThat(version.getSpec().getStrictly(), nullValue())
      assertThat(version.getSpec().getPrefer(), nullValue())
      assertThat(versions.toList()[6].first, equalTo("illegalVersion"))
    }

    run {
      val version = versions.toList()[7].second
      assertThat(version.getSpec().compactNotation(), nullValue())
      assertThat(version.getSpec().getRequire(), nullValue())
      assertThat(version.getSpec().getStrictly(), nullValue())
      assertThat(version.getSpec().getPrefer(), nullValue())
      assertThat(versions.toList()[7].first, equalTo("illegalVersion2"))
    }

    run {
      val version = versions.toList()[8].second
      assertThat(version.getSpec().compactNotation(), nullValue())
      assertThat(version.getSpec().getRequire(), nullValue())
      assertThat(version.getSpec().getStrictly(), nullValue())
      assertThat(version.getSpec().getPrefer(), nullValue())
      assertThat(versions.toList()[8].first, equalTo("illegalVersion3"))
    }
  }

  @Test
  fun testAllAliases() {
    writeToBuildFile("")
    writeToVersionCatalogFile(TestFile.GET_ALL_DECLARATIONS)

    val catalogModel = projectBuildModel.versionCatalogsModel

    val versions = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations().getAllAliases()
    assertSize(9, versions.toList())
    assertEquals(versions, setOf("literalVersion", "literalVersion2", "literalVersion3",
                                 "mapVersion", "mapVersion2", "mapVersion3",
                                 "illegalVersion", "illegalVersion2", "illegalVersion3"))
  }

  @Test
  fun testRemove() {
    writeToBuildFile("")
    writeToVersionCatalogFile(TestFile.GET_ALL_DECLARATIONS)

    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val versions = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations()
    assertSize(9, versions.getAll().toList())

    versions.remove("literalVersion")
    assertSize(8, versions.getAll().toList())

    applyChangesAndReparse(buildModel)

    val versionsToCheck = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations()
    assertSize(8, versions.getAll().toList())
    assertFalse(versionsToCheck.getAll().contains("literalVersion"))
  }

  @Test
  fun testAddVersionAsLiteral() {
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations()
    declarations.addDeclaration("core", "1.0.0")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [versions]
      core = "1.0.0"
    """.trimIndent())
  }

  @Test
  fun testAddVersionAsLiteralWithComplexName() {
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations()
    declarations.addDeclaration("api.core", "1.0.0")
    declarations.addDeclaration("test_test", "2.0.0")
    declarations.addDeclaration("ui-composition", "3.0.0")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [versions]
      "api.core" = "1.0.0"
      test_test = "2.0.0"
      ui-composition = "3.0.0"
    """.trimIndent())
  }

  @Test
  fun testUpdateVersionAsLiteral() {
    writeToBuildFile("")
    writeToVersionCatalogFile("""
      [versions]
      core = "1.0.0"
    """.trimIndent())
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations()
    val versionModel = declarations.getAll()["core"]!!
    versionModel.require().setValue("1.1.0")
    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [versions]
      core = "1.1.0"
    """.trimIndent())
  }

  @Test
  fun testUpdateVersionAsMap() {
    writeToBuildFile("")
    writeToVersionCatalogFile("""
      [versions]
      core = { required = "1.0.0", prefer = "1.0.1" }
    """.trimIndent())
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations()
    val versionModel = declarations.getAll()["core"]!!
    versionModel.prefer().setValue("1.1.0")
    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [versions]
      core = { required = "1.0.0", prefer = "1.1.0" }
    """.trimIndent())
  }

  @Test
  fun testUpdateVersionAsLiteral2() {
    writeToBuildFile("")
    writeToVersionCatalogFile("""
      [versions]
      core = "[0.0.1,1.0.0]!!1.0.0"
    """.trimIndent())
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations()
    val versionModel = declarations.getAll()["core"]!!
    versionModel.strictly().setValue("[0.0.1,2.0.0]")
    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [versions]
      core = "[0.0.1,2.0.0]!!1.0.0"
    """.trimIndent())
  }


  @Test
  fun testRemoveNonExistent() {
    writeToBuildFile("")
    writeToVersionCatalogFile(TestFile.GET_ALL_DECLARATIONS)

    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val versions = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations()
    assertSize(9, versions.getAll().toList())

    versions.remove("NonExistent")
    assertSize(9, versions.getAll().toList())
  }


  internal enum class TestFile(private val path: @SystemDependent String) : TestFileName {
    GET_ALL_DECLARATIONS("allVersions.versions.toml"), ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/versionCatalogVersionDeclarationModel/$path", extension)
    }
  }
}