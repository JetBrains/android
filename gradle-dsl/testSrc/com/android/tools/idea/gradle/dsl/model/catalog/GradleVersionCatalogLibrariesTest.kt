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
import com.android.tools.idea.gradle.dsl.model.dependencies.LibraryDeclarationSpecImpl
import org.gradle.groovy.scripts.internal.BuildOperationBackedScriptCompilationHandler.GROOVY_LANGUAGE
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import org.junit.runners.Parameterized
import java.io.File

@Suppress("ACCIDENTAL_OVERRIDE")
class GradleVersionCatalogLibrariesTest : GradleFileModelTestCase() {

  companion object {
    /**
     * Declare only one parameter - Groovy Language. That is, all test projects will be
     * created with Groovy build files only. Most/all build files intended to be empty here
     * as we test mainly catalog model.
     * Despite that one parameter Groovy configuration is a stub for now, it can be
     * transformed in future in the full kotlin/groovy parametrized test
     */
    @Contract(pure = true)
    @Parameterized.Parameters(name = "{1}")
    @JvmStatic
    fun languageExtensions(): Collection<*> {
      return listOf(
        arrayOf<Any>(".gradle", GROOVY_LANGUAGE),
      )
    }
  }
  @Test
  fun testAllDependencies() {
    writeToBuildFile("")
    writeToVersionCatalogFile(TestFile.GET_ALL_DECLARATIONS)

    val catalogModel = projectBuildModel.versionCatalogsModel

    val deps = catalogModel.getVersionCatalogModel("libs").libraryDeclarations().getAll()
    assertSize(8, deps.toList())
    run {
      val dep = deps.toList()[0].second
      MatcherAssert.assertThat(dep.compactNotation(), equalTo("junit:junit:4.13.2"))
      MatcherAssert.assertThat(dep.name().toString(), equalTo("junit"))
      MatcherAssert.assertThat(dep.group().toString(), equalTo("junit"))
      MatcherAssert.assertThat(dep.version().toString(), equalTo("4.13.2"))
      MatcherAssert.assertThat(deps.toList()[0].first, equalTo("junit"))
    }
    run {
      val dep = deps.toList()[1].second
      MatcherAssert.assertThat(dep.compactNotation(), equalTo("androidx.core:core-ktx:1.8.0"))
      MatcherAssert.assertThat(dep.name().toString(), equalTo("core-ktx"))
      MatcherAssert.assertThat(dep.group().toString(), equalTo("androidx.core"))
      MatcherAssert.assertThat(dep.version().toString(), equalTo("1.8.0"))
      MatcherAssert.assertThat(deps.toList()[1].first, equalTo("core"))
    }
    run {
      val dep = deps.toList()[2].second
      MatcherAssert.assertThat(dep.compactNotation(), equalTo("androidx.appcompat:appcompat:1.3.0"))
      MatcherAssert.assertThat(dep.name().toString(), equalTo("appcompat"))
      MatcherAssert.assertThat(dep.group().toString(), equalTo("androidx.appcompat"))
      MatcherAssert.assertThat(dep.version().toString(), equalTo("1.3.0"))
      MatcherAssert.assertThat(deps.toList()[2].first, equalTo("appcompat"))
    }
    run {
      val dep = deps.toList()[3].second
      MatcherAssert.assertThat(dep.compactNotation(), equalTo("com.google.android.material:material:1.5.0"))
      MatcherAssert.assertThat(dep.name().toString(), equalTo("material"))
      MatcherAssert.assertThat(dep.group().toString(), equalTo("com.google.android.material"))
      MatcherAssert.assertThat(dep.version().toString(), equalTo("1.5.0"))
      MatcherAssert.assertThat(deps.toList()[3].first, equalTo("material"))
    }
    run {
      val dep = deps.toList()[4].second
      MatcherAssert.assertThat(dep.compactNotation(), equalTo("androidx.constraintlayout:constraintlayout:2.1.3"))
      MatcherAssert.assertThat(dep.name().toString(), equalTo("constraintlayout"))
      MatcherAssert.assertThat(dep.group().toString(), equalTo("androidx.constraintlayout"))
      MatcherAssert.assertThat(dep.version().toString(), equalTo("2.1.3"))
      MatcherAssert.assertThat(deps.toList()[4].first, equalTo("constraintlayout"))
    }
    run {
      val dep = deps.toList()[5].second
      MatcherAssert.assertThat(dep.compactNotation(), equalTo("androidx.navigation:navigation-ui:2.5.2"))
      MatcherAssert.assertThat(dep.name().toString(), equalTo("navigation-ui"))
      MatcherAssert.assertThat(dep.group().toString(), equalTo("androidx.navigation"))
      MatcherAssert.assertThat(dep.version().toString(), equalTo("2.5.2"))
      MatcherAssert.assertThat(deps.toList()[5].first, equalTo("nav_ui"))
    }
    run {
      val dep = deps.toList()[6].second
      MatcherAssert.assertThat(dep.compactNotation(), equalTo("androidx.test.espresso:espresso-core:3.4.0"))
      MatcherAssert.assertThat(dep.name().toString(), equalTo("espresso-core"))
      MatcherAssert.assertThat(dep.group().toString(), equalTo("androidx.test.espresso"))
      MatcherAssert.assertThat(dep.version().toString(), equalTo("3.4.0"))
      MatcherAssert.assertThat(deps.toList()[6].first, equalTo("espressocore"))
    }
    run {
      val dep = deps.toList()[7].second
      MatcherAssert.assertThat(dep.compactNotation(), equalTo("androidx.navigation:navigation-fragment"))
      MatcherAssert.assertThat(dep.name().toString(), equalTo("navigation-fragment"))
      MatcherAssert.assertThat(dep.group().toString(), equalTo("androidx.navigation"))
      MatcherAssert.assertThat(dep.version().toString(), CoreMatchers.nullValue())
      MatcherAssert.assertThat(deps.toList()[7].first, equalTo("nav-fragment"))
    }

  }

  @Test
  fun testAllAliases() {
    writeToBuildFile("")
    writeToVersionCatalogFile(TestFile.GET_ALL_DECLARATIONS)

    val catalogModel = projectBuildModel.versionCatalogsModel

    val deps = catalogModel.getVersionCatalogModel("libs").libraryDeclarations().getAllAliases()
    assertSize(8, deps.toList())
    assertEquals(deps, setOf("junit", "core", "appcompat", "material", "constraintlayout", "nav_ui", "espressocore", "nav-fragment"))
  }

  @Test
  fun testAddDeclaration(){
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs").libraryDeclarations()
    declarations.addDeclaration("core","androidx.core:core-ktx:1.8.0")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [libraries]
      core = { group="androidx.core", name="core-ktx", version="1.8.0" }
    """.trimIndent())
  }

  @Test
  fun testAddDeclaration2(){
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs").libraryDeclarations()
    val spec = LibraryDeclarationSpecImpl("core-ktx", "androidx.core", "1.8.0")
    declarations.addDeclaration("core", spec)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [libraries]
      core = { group="androidx.core", name="core-ktx", version="1.8.0" }
    """.trimIndent())
  }

  @Test
  fun testAddDeclarationWithEmptyVersion(){
    // BOM case
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs").libraryDeclarations()
    val spec = LibraryDeclarationSpecImpl("core-ktx", "androidx.core", null)
    declarations.addDeclaration("core", spec)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [libraries]
      core = { group="androidx.core", name="core-ktx" }
    """.trimIndent())
  }

  @Test
  fun testRemoveDeclaration(){
    writeToBuildFile("")
    writeToVersionCatalogFile("""
      [versions]
      appcompat = "1.3.0"
      [libraries]
      core = { group="androidx.core", name="core-ktx", version="1.8.0" }
      appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
    """.trimIndent())
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs").libraryDeclarations()
    declarations.remove("appcompat")
    applyChanges(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [versions]
      appcompat = "1.3.0"
      [libraries]
      core = { group="androidx.core", name="core-ktx", version="1.8.0" }
    """.trimIndent())
  }

  @Test
  fun testRemoveLastDeclaration(){
    writeToBuildFile("")
    writeToVersionCatalogFile("""
      [libraries]
      core = { group="androidx.core", name="core-ktx", version="1.8.0" }
    """.trimIndent())
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs").libraryDeclarations()
    declarations.remove("core")
    applyChanges(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [libraries]
    """.trimIndent())
  }

  internal enum class TestFile(private val path: @SystemDependent String) : TestFileName {
    GET_ALL_DECLARATIONS("allDeclarations.versions.toml"), ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/versionCatalogLibraryDeclarationModel/$path", extension)
    }
  }


}