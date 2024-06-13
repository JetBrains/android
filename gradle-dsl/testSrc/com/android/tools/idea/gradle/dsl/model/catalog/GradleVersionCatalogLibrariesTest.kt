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
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.model.dependencies.LibraryDeclarationSpecImpl
import com.android.tools.idea.gradle.dsl.model.dependencies.VersionDeclarationSpecImpl
import com.intellij.openapi.command.WriteCommandAction
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File

class GradleVersionCatalogLibrariesTest : GradleFileModelTestCase() {

  @Test
  fun testAllDependencies() {
    writeToBuildFile("")
    writeToVersionCatalogFile(TestFile.GET_ALL_DECLARATIONS)

    val catalogModel = projectBuildModel.versionCatalogsModel

    val deps = catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations().getAll()
    assertSize(13, deps.toList())
    run {
      val dep = deps.toList()[0].second
      assertThat(dep.compactNotation(), equalTo("junit:junit:4.13.2"))
      assertThat(dep.name().toString(), equalTo("junit"))
      assertThat(dep.group().toString(), equalTo("junit"))
      assertThat(dep.version().compactNotation(), equalTo("4.13.2"))
      assertThat(dep.version().getSpec().getRequire(), equalTo("4.13.2"))
      assertThat(deps.toList()[0].first, equalTo("junit"))
    }
    run {
      val dep = deps.toList()[1].second
      assertThat(dep.compactNotation(), equalTo("androidx.core:core-ktx:1.8.0"))
      assertThat(dep.name().toString(), equalTo("core-ktx"))
      assertThat(dep.group().toString(), equalTo("androidx.core"))
      assertThat(dep.version().compactNotation(), equalTo("1.8.0"))
      assertThat(deps.toList()[1].first, equalTo("core"))
    }
    run {
      val dep = deps.toList()[2].second
      assertThat(dep.compactNotation(), equalTo("androidx.appcompat:appcompat:1.3.0"))
      assertThat(dep.name().toString(), equalTo("appcompat"))
      assertThat(dep.group().toString(), equalTo("androidx.appcompat"))
      assertThat(dep.version().compactNotation(), equalTo("1.3.0"))
      assertThat(dep.version().getSpec().getRequire(), equalTo("1.3.0"))
      assertThat(deps.toList()[2].first, equalTo("appcompat"))
    }
    run {
      val dep = deps.toList()[3].second
      assertThat(dep.compactNotation(), equalTo("com.google.android.material:material:1.5.0"))
      assertThat(dep.name().toString(), equalTo("material"))
      assertThat(dep.group().toString(), equalTo("com.google.android.material"))
      assertThat(dep.version().compactNotation(), equalTo("1.5.0"))
      assertThat(deps.toList()[3].first, equalTo("material"))
    }
    run {
      val dep = deps.toList()[4].second
      assertThat(dep.compactNotation(), equalTo("androidx.constraintlayout:constraintlayout:2.1.3"))
      assertThat(dep.name().toString(), equalTo("constraintlayout"))
      assertThat(dep.group().toString(), equalTo("androidx.constraintlayout"))
      assertThat(dep.version().compactNotation(), equalTo("2.1.3"))
      assertThat(deps.toList()[4].first, equalTo("constraintlayout"))
    }
    run {
      val dep = deps.toList()[5].second
      assertThat(dep.compactNotation(), equalTo("androidx.navigation:navigation-ui:2.5.2"))
      assertThat(dep.name().toString(), equalTo("navigation-ui"))
      assertThat(dep.group().toString(), equalTo("androidx.navigation"))
      assertThat(dep.version().compactNotation(), equalTo("2.5.2"))
      assertThat(deps.toList()[5].first, equalTo("nav_ui"))
    }
    run {
      val dep = deps.toList()[6].second
      assertThat(dep.compactNotation(), equalTo("androidx.test.espresso:espresso-core:3.4.0"))
      assertThat(dep.name().toString(), equalTo("espresso-core"))
      assertThat(dep.group().toString(), equalTo("androidx.test.espresso"))
      assertThat(dep.version().compactNotation(), equalTo("3.4.0"))
      assertThat(deps.toList()[6].first, equalTo("espressocore"))
    }
    run {
      val dep = deps.toList()[7].second
      assertThat(dep.compactNotation(), equalTo("androidx.navigation:navigation-fragment"))
      assertThat(dep.name().toString(), equalTo("navigation-fragment"))
      assertThat(dep.group().toString(), equalTo("androidx.navigation"))
      assertThat(dep.version().compactNotation(), nullValue())
      assertThat(deps.toList()[7].first, equalTo("nav-fragment"))
    }
    run {
      val dep = deps.toList()[8].second
      assertThat(dep.compactNotation(), equalTo("com.google.android.material:material:[1.0.0,2.1.0]!!2.0.1"))
      assertThat(dep.name().toString(), equalTo("material"))
      assertThat(dep.group().toString(), equalTo("com.google.android.material"))
      with(dep.version().getSpec()) {
        assertThat(getRequire(), nullValue())
        assertThat(getPrefer(), equalTo("2.0.1"))
        assertThat(getStrictly(), equalTo("[1.0.0,2.1.0]"))
      }
      assertThat(deps.toList()[8].first, equalTo("material2"))
    }
    run {
      val dep = deps.toList()[9].second
      // once we have too many version attributes we show only require in compact notation
      assertThat(dep.compactNotation(), equalTo("com.google.android.material:material"))
      assertThat(dep.name().toString(), equalTo("material"))
      assertThat(dep.version().compactNotation(), nullValue())
      assertThat(dep.group().toString(), equalTo("com.google.android.material"))
      with(dep.version().getSpec()) {
        assertThat(getRequire(), equalTo("1.4"))
        assertThat(getPrefer(), equalTo("2.0.1"))
        assertThat(getStrictly(), equalTo("[1.0.0,2.1.0]"))
      }
      assertThat(deps.toList()[9].first, equalTo("material3"))
    }
    run {
      val dep = deps.toList()[10].second
      assertThat(dep.compactNotation(), equalTo("com.google.android.material:material:[1.0.0,2.1.0]!!2.0.1"))
      assertThat(dep.name().toString(), equalTo("material"))
      assertThat(dep.group().toString(), equalTo("com.google.android.material"))
      with(dep.version().getSpec()) {
        assertThat(getRequire(), nullValue())
        assertThat(getPrefer(), equalTo("2.0.1"))
        assertThat(getStrictly(), equalTo("[1.0.0,2.1.0]"))
      }
      assertThat(deps.toList()[10].first, equalTo("material4"))
    }
    run {
      val dep = deps.toList()[11].second
      assertThat(dep.compactNotation(), equalTo("com.google.android.material:material:[1.0.0,2.1.0]!!2.0.1"))
      assertThat(dep.name().toString(), equalTo("material"))
      assertThat(dep.group().toString(), equalTo("com.google.android.material"))
      with(dep.version().getSpec()) {
        assertThat(getRequire(), nullValue())
        assertThat(getPrefer(), equalTo("2.0.1"))
        assertThat(getStrictly(), equalTo("[1.0.0,2.1.0]"))
      }
      assertThat(deps.toList()[11].first, equalTo("material5"))
    }
    run {
      val dep = deps.toList()[12].second
      assertThat(dep.compactNotation(), equalTo("com.google.android.material:material:[1.0.0,2.1.0]!!"))
      assertThat(dep.name().toString(), equalTo("material"))
      assertThat(dep.group().toString(), equalTo("com.google.android.material"))
      with(dep.version().getSpec()) {
        assertThat(getRequire(), nullValue())
        assertThat(getPrefer(), nullValue())
        assertThat(getStrictly(), equalTo("[1.0.0,2.1.0]"))
      }
      assertThat(deps.toList()[12].first, equalTo("material6"))
    }

  }

  @Test
  fun testAllAliases() {
    writeToBuildFile("")
    writeToVersionCatalogFile(TestFile.GET_ALL_DECLARATIONS)

    val catalogModel = projectBuildModel.versionCatalogsModel

    val deps = catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations().getAllAliases()
    assertSize(13, deps.toList())
    assertEquals(deps, setOf("junit", "core", "appcompat", "material", "constraintlayout", "nav_ui", "espressocore", "nav-fragment",
                             "material2", "material3", "material4", "material5", "material6"))
  }

  @Test
  fun testAddDeclarationAsMap() {
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations()
    val spec = LibraryDeclarationSpecImpl("core-ktx", "androidx.core", VersionDeclarationSpecImpl.create("1.8.0"))
    declarations.addDeclaration("core", spec)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [libraries]
      core = { group="androidx.core", name="core-ktx", version="1.8.0" }
    """.trimIndent())
  }

  @Test
  fun testAddDeclarationAsMap2() {
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations()
    val spec = LibraryDeclarationSpecImpl("core-ktx", "androidx.core", VersionDeclarationSpecImpl.create("1.8.0"))

    // probably only version setter will be useful
    spec.setVersion(VersionDeclarationSpecImpl.create("1.9.0"))
    spec.setName("core-ktx2")
    spec.setGroup("androidx.core2")

    declarations.addDeclaration("core", spec)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [libraries]
      core = { group="androidx.core2", name="core-ktx2", version="1.9.0" }
    """.trimIndent())
  }

  @Test
  fun testAddDeclarationAsLiteral() {
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations()
    declarations.addDeclaration("core", "core-ktx:androidx.core:1.8.0")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [libraries]
      core = "core-ktx:androidx.core:1.8.0"
    """.trimIndent())
  }

  @Test
  fun testAddDeclarationAsMapWithVersionRef() {
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val versions = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations()
    val declarations = catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations()

    val versionDeclaration = versions.addDeclaration("coreVersion", "1.8.0")
    declarations.addDeclaration("core", "core-ktx", "androidx.core", ReferenceTo(versionDeclaration!!, declarations))

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [versions]
      coreVersion = "1.8.0"
      [libraries]
      core = { group="androidx.core", name="core-ktx", version.ref="coreVersion" }
    """.trimIndent())
  }

  @Test
  fun testAddDeclarationAsMapWithVersionRef2() {
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val versions = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations()
    val declarations = catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations()

    val versionDeclaration = versions.addDeclaration("coreVersion",
                                                     VersionDeclarationSpecImpl.create("[1.6.0,1.8.0]!!1.8.0")!!)
    declarations.addDeclaration("core", "core-ktx", "androidx.core", ReferenceTo(versionDeclaration!!, declarations))

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [versions]
      coreVersion = { strictly = "[1.6.0,1.8.0]", prefer = "1.8.0" }
      [libraries]
      core = { group="androidx.core", name="core-ktx", version.ref="coreVersion" }
    """.trimIndent())
  }

  @Test
  fun testAddDeclarationAsMapWithEmptyVersion2() {
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val versions = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations()
    catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations()

    val versionDeclaration = versions.addDeclaration("coreVersion", "")
    assertNull(versionDeclaration)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [versions]
      [libraries]
    """.trimIndent())
  }

  @Test
  fun testAddDeclarationAsMapWithLiteralVersion() {
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations()
    val spec = LibraryDeclarationSpecImpl("core-ktx", "androidx.core",
                                          VersionDeclarationSpecImpl.create( "[1.6.0,1.8.0]!!1.8.0")!!)

    declarations.addDeclaration("core", spec)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [libraries]
      core = { group="androidx.core", name="core-ktx", version="[1.6.0,1.8.0]!!1.8.0" }
    """.trimIndent())
  }

  @Test
  fun testAddDeclarationWithEmptyVersion() {
    // BOM case
    writeToBuildFile("")
    writeToVersionCatalogFile("")
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations()
    val spec = LibraryDeclarationSpecImpl("core-ktx", "androidx.core", null)
    declarations.addDeclaration("core", spec)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [libraries]
      core = { group="androidx.core", name="core-ktx" }
    """.trimIndent())
  }

  @Test
  fun testUpdateVersionInLiteralDeclaration() {
    writeToBuildFile("")
    writeToVersionCatalogFile("""
      [libraries]
      core = "androidx.core:core-ktx:1.8.0"
    """.trimIndent())
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations()
    val versionModel = declarations.getAll()["core"]!!.version()
    versionModel.require().setValue("1.9.0")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [libraries]
      core = "androidx.core:core-ktx:1.9.0"
    """.trimIndent())
  }

  @Test
  fun testRemoveDeclaration() {
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

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations()
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
  fun testUpdateVersionLiteral() {
    doCatalogTest("""
      [libraries]
      core = "androidx.core:core-ktx:1.8.0"
    """.trimIndent(),"""
      [libraries]
      core = "androidx.core:core-ktx:1.8.1"
    """.trimIndent()) { catalog ->
      val declarations = catalog.libraryDeclarations()
      val declaration = declarations.getAll()["core"]!!
      declaration.updateVersion("1.8.1")
    }
  }

  @Test
  fun testUpdateVersionFromMap() {
    doCatalogTest("""
      [libraries]
      core = { group="androidx.core", name="core-ktx", version="1.8.0" }
    """.trimIndent(),"""
      [libraries]
      core = { group="androidx.core", name="core-ktx", version="1.8.1" }
    """.trimIndent()) { model ->
      val declarations = model.libraryDeclarations()
      val declaration = declarations.getAll()["core"]!!
      WriteCommandAction.runWriteCommandAction(project) {
        declaration.updateVersion("1.8.1")
      }
    }
  }

  @Test
  fun testUpdateVersionWithReference() {
    doCatalogTest("""
      [versions]
      coreVersion = "1.8.0"
      [libraries]
      core = { group="androidx.core", name="core-ktx", version.ref="coreVersion" }
    """.trimIndent(), """
      [versions]
      coreVersion = "1.8.0"
      newVersion = "1.8.1"
      [libraries]
      core = { group="androidx.core", name="core-ktx", version.ref="newVersion" }
    """.trimIndent()) { catalog ->
      val versions = catalog.versionDeclarations()
      versions.addDeclaration("newVersion", "1.8.1")
      val newVersion = versions.getAll()["newVersion"]!!
      val declaration = catalog.libraryDeclarations().getAll()["core"]!!
      WriteCommandAction.runWriteCommandAction(project) {
        declaration.updateVersion(newVersion)
      }
    }
  }

  @Test
  fun testUpdateVersionFromMap2() {
    doCatalogTest("""
      [libraries]
      core = { group="androidx.core", name="core-ktx", version = { require = "1.8.0"} }
    """.trimIndent(), """
      [libraries]
      core = { group="androidx.core", name="core-ktx", version="1.8.1" }
    """.trimIndent()) { model ->
      val declarations = model.libraryDeclarations()
      val declaration = declarations.getAll()["core"]!!
      WriteCommandAction.runWriteCommandAction(project) {
        declaration.updateVersion("1.8.1")
      }
    }
  }

  @Test
  fun testRemoveLastDeclaration() {
    writeToBuildFile("")
    writeToVersionCatalogFile("""
      [libraries]
      core = { group="androidx.core", name="core-ktx", version="1.8.0" }
    """.trimIndent())
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel

    val declarations = catalogModel.getVersionCatalogModel("libs")!!.libraryDeclarations()
    declarations.remove("core")
    applyChanges(buildModel)
    verifyFileContents(myVersionCatalogFile, """
      [libraries]
    """.trimIndent())
  }

  fun doCatalogTest(initialCatalog: String, catalogToCompare: String, change: (GradleVersionCatalogModel) -> Unit) {
    writeToBuildFile("")
    writeToVersionCatalogFile(initialCatalog)
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel
    val catalog = catalogModel.getVersionCatalogModel("libs")!!

    change.invoke(catalog)
    WriteCommandAction.runWriteCommandAction(project) {
      applyChanges(buildModel)
    }
    verifyFileContents(myVersionCatalogFile, catalogToCompare)
  }

  internal enum class TestFile(private val path: @SystemDependent String) : TestFileName {
    GET_ALL_DECLARATIONS("allDeclarations.versions.toml"), ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/versionCatalogLibraryDeclarationModel/$path", extension)
    }
  }


}
