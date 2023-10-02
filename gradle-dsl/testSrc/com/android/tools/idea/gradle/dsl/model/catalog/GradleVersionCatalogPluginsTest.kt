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
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.model.dependencies.PluginDeclarationSpecImpl
import com.android.tools.idea.gradle.dsl.model.dependencies.VersionDeclarationSpecImpl
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.annotations.SystemDependent
import org.junit.Ignore
import org.junit.Test
import java.io.File

class GradleVersionCatalogPluginsTest : GradleFileModelTestCase() {
  @Test
  fun testAllPlugins() {
    writeToBuildFile("")
    writeToVersionCatalogFile(TestFile.GET_ALL_DECLARATIONS)

    val catalogModel = projectBuildModel.versionCatalogsModel

    val plugins = catalogModel.getVersionCatalogModel("libs")!!.pluginDeclarations().getAll()
    assertSize(5, plugins.toList())
    run {
      val plugin = plugins.toList()[0].second
      assertThat(plugin.compactNotation(), CoreMatchers.equalTo("fake.plugin:1.0"))
      assertThat(plugin.id().toString(), CoreMatchers.equalTo("fake.plugin"))
      assertThat(plugin.version().compactNotation(), CoreMatchers.equalTo("1.0"))
      assertThat(plugin.version().getSpec().getRequire(), CoreMatchers.equalTo("1.0"))
      assertThat(plugins.toList()[0].first, CoreMatchers.equalTo("fakePlugin"))
    }
    run {
      val plugin = plugins.toList()[1].second
      assertThat(plugin.compactNotation(), CoreMatchers.equalTo("com.example.foo:2.0"))
      assertThat(plugin.id().toString(), CoreMatchers.equalTo("com.example.foo"))
      assertThat(plugin.version().compactNotation(), CoreMatchers.equalTo("2.0"))
      assertThat(plugins.toList()[1].first, CoreMatchers.equalTo("foo"))
    }
    run {
      val plugin = plugins.toList()[2].second
      assertThat(plugin.compactNotation(), CoreMatchers.equalTo("com.example.bar:1.0.0"))
      assertThat(plugin.id().toString(), CoreMatchers.equalTo("com.example.bar"))
      assertThat(plugin.version().compactNotation(), CoreMatchers.equalTo("1.0.0"))
      assertThat(plugin.version().getSpec().getRequire(), CoreMatchers.equalTo("1.0.0"))
      assertThat(plugins.toList()[2].first, CoreMatchers.equalTo("bar"))
    }
    run {
      val plugin = plugins.toList()[3].second
      assertThat(plugin.compactNotation(), CoreMatchers.equalTo("com.android.application:8.0.0-beta01"))
      assertThat(plugin.id().toString(), CoreMatchers.equalTo("com.android.application"))
      assertThat(plugin.version().compactNotation(), CoreMatchers.equalTo("8.0.0-beta01"))
      assertThat(plugins.toList()[3].first, CoreMatchers.equalTo("android_application"))
    }
    run {
      val plugin = plugins.toList()[4].second
      assertThat(plugin.compactNotation(), CoreMatchers.equalTo("org.jetbrains.kotlin.android:1.7.20"))
      assertThat(plugin.id().toString(), CoreMatchers.equalTo("org.jetbrains.kotlin.android"))
      assertThat(plugin.version().compactNotation(), CoreMatchers.equalTo("1.7.20"))
      assertThat(plugins.toList()[4].first, CoreMatchers.equalTo("kotlinAndroid"))
    }
  }

  @Test
  fun testAllAliases() {
    writeToBuildFile("")
    writeToVersionCatalogFile(TestFile.GET_ALL_DECLARATIONS)

    val catalogModel = projectBuildModel.versionCatalogsModel

    val plugins = catalogModel.getVersionCatalogModel("libs")!!.pluginDeclarations().getAllAliases()
    assertSize(5, plugins.toList())
    assertEquals(plugins, setOf("fakePlugin", "foo", "bar", "android_application", "kotlinAndroid"))
  }

  @Test
  fun testAddPluginAsMap() {
    prepareAddTest { buildModel, catalogModel ->
      val declarations = catalogModel.getVersionCatalogModel("libs")!!.pluginDeclarations()
      val spec = PluginDeclarationSpecImpl("foo", VersionDeclarationSpecImpl.create("1.8.0")!!)
      declarations.addDeclaration("fooPlugin", spec)

      applyChangesAndReparse(buildModel)
      verifyFileContents(myVersionCatalogFile, """
      [plugins]
      fooPlugin = { id="foo", version="1.8.0" }
    """.trimIndent())
    }
  }

  @Test
  fun testAddPluginAsMap2() {
    prepareAddTest { buildModel, catalogModel ->

      val declarations = catalogModel.getVersionCatalogModel("libs")!!.pluginDeclarations()
      val spec = PluginDeclarationSpecImpl("foo", VersionDeclarationSpecImpl.create("1.8.0")!!)

      // probably only version setter will be useful
      spec.setVersion(VersionDeclarationSpecImpl.create("1.9.0")!!)
      spec.setId("foo2")

      declarations.addDeclaration("fooPlugin", spec)

      applyChangesAndReparse(buildModel)
      verifyFileContents(myVersionCatalogFile, """
      [plugins]
      fooPlugin = { id="foo2", version="1.9.0" }
    """.trimIndent())
    }
  }

  @Test
  fun testAddDeclarationAsLiteral() {
    prepareAddTest { buildModel, catalogModel ->

      val declarations = catalogModel.getVersionCatalogModel("libs")!!.pluginDeclarations()
      declarations.addDeclaration("fooPlugin", "foo:1.8.0")

      applyChangesAndReparse(buildModel)
      verifyFileContents(myVersionCatalogFile, """
      [plugins]
      fooPlugin = "foo:1.8.0"
    """.trimIndent())
    }
  }

  @Test
  fun testAddDeclarationAsMapWithVersionRef() {
    prepareAddTest { buildModel, catalogModel ->

      val versions = catalogModel.getVersionCatalogModel("libs")!!.versionDeclarations()
      val declarations = catalogModel.getVersionCatalogModel("libs")!!.pluginDeclarations()

      val versionDeclaration = versions.addDeclaration("fooVersion", "1.8.0")
      declarations.addDeclaration("fooPlugin", "foo", ReferenceTo(versionDeclaration!!, declarations))

      applyChangesAndReparse(buildModel)
      verifyFileContents(myVersionCatalogFile, """
      [versions]
      fooVersion = "1.8.0"
      [plugins]
      fooPlugin = { id = "foo", version.ref = "fooVersion" }
    """.trimIndent())
    }
  }

  @Test
  fun testAddDeclarationAsMapWithLiteralVersion() {
    prepareAddTest { buildModel, catalogModel ->

      val declarations = catalogModel.getVersionCatalogModel("libs")!!.pluginDeclarations()
      val spec = PluginDeclarationSpecImpl("foo",
                                           VersionDeclarationSpecImpl.create("[1.6.0,1.8.0]!!1.8.0")!!)

      declarations.addDeclaration("fooPlugin", spec)

      applyChangesAndReparse(buildModel)
      verifyFileContents(myVersionCatalogFile, """
      [plugins]
      fooPlugin = { id = "foo", version = "[1.6.0,1.8.0]!!1.8.0" }
    """.trimIndent())
    }
  }

  // We do not support writing in such format for now
  @Test
  @Ignore("b/303108936")
  fun testAddDeclarationAsMapWithMapVersion() {
    prepareAddTest { buildModel, catalogModel ->

      val declarations = catalogModel.getVersionCatalogModel("libs")!!.pluginDeclarations()
      val spec = PluginDeclarationSpecImpl("foo",
                                           VersionDeclarationSpecImpl.create("[1.6.0,1.8.0]!!1.8.0")!!)

      declarations.addDeclaration("fooPlugin", spec)

      applyChangesAndReparse(buildModel)
      verifyFileContents(myVersionCatalogFile, """
      [plugins]
      fooPlugin = { id = "foo", version = { strictly = "[1.6.0,1.8.0]", prefer = "1.8.0"} }
    """.trimIndent())
    }
  }


  @Test
  fun testUpdateVersionInLiteralDeclaration() {
    prepareUpdateTest("""
      [plugins]
      fooPlugin = "foo:1.8.0"
    """.trimIndent()) { buildModel, catalogModel ->

      val declarations = catalogModel.getVersionCatalogModel("libs")!!.pluginDeclarations()
      val versionModel = declarations.getAll()["fooPlugin"]!!.version()
      versionModel.require().setValue("1.9.0")

      applyChangesAndReparse(buildModel)
      verifyFileContents(myVersionCatalogFile, """
      [plugins]
      fooPlugin = "foo:1.9.0"
    """.trimIndent())
    }
  }

  @Test
  fun testRemoveDeclaration() {
    prepareUpdateTest("""
      [versions]
      foo_version = "1.3.0"
      [plugins]
      android_application = { id = "com.android.application", version = "8.0.0-beta01" }
      foo = { id = "foo", version.ref = "foo_version" }
    """.trimIndent()) { buildModel, catalogModel ->
      val declarations = catalogModel.getVersionCatalogModel("libs")!!.pluginDeclarations()
      declarations.remove("foo")
      applyChanges(buildModel)
      verifyFileContents(myVersionCatalogFile, """
      [versions]
      foo_version = "1.3.0"
      [plugins]
      android_application = { id = "com.android.application", version = "8.0.0-beta01" }
    """.trimIndent())
    }
  }

  @Test
  fun testRemoveLastDeclaration() {
    prepareUpdateTest("""
      [plugins]
      android_application = { id = "com.android.application", version = "8.0.0-beta01" }
    """.trimIndent()) { buildModel, catalogModel ->
      val declarations = catalogModel.getVersionCatalogModel("libs")!!.pluginDeclarations()
      declarations.remove("android_application")
      applyChanges(buildModel)
      verifyFileContents(myVersionCatalogFile, """
      [plugins]
    """.trimIndent())
    }
  }

  private fun prepareAddTest(f: (ProjectBuildModel, GradleVersionCatalogsModel) -> Unit) {
    prepareUpdateTest("", f)
  }

  private fun prepareUpdateTest(catalogContent:String, f: (ProjectBuildModel, GradleVersionCatalogsModel) -> Unit) {
    writeToBuildFile("")
    writeToVersionCatalogFile(catalogContent)
    val buildModel = projectBuildModel
    val catalogModel = buildModel.versionCatalogsModel
    f(buildModel, catalogModel)
  }

  internal enum class TestFile(private val path: @SystemDependent String) : TestFileName {
    GET_ALL_DECLARATIONS("allDeclarations.versions.toml"), ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/versionCatalogPluginDeclarationModel/$path", extension)
    }
  }
}
