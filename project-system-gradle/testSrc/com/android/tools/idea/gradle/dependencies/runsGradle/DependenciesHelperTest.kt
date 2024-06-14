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
package com.android.tools.idea.gradle.dependencies.runsGradle

import com.android.tools.idea.gradle.dependencies.DependenciesHelper
import com.android.tools.idea.gradle.dependencies.DependenciesInserter
import com.android.tools.idea.gradle.dependencies.ExactDependencyMatcher
import com.android.tools.idea.gradle.dependencies.FalsePluginMatcher
import com.android.tools.idea.gradle.dependencies.GroupNameDependencyMatcher
import com.android.tools.idea.gradle.dependencies.IdPluginMatcher
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.BuildEnvironment
import com.android.tools.idea.testing.TestProjectPaths.MIGRATE_BUILD_CONFIG
import com.android.tools.idea.testing.TestProjectPaths.MINIMAL_CATALOG_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_PLUGINS_DSL
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.getTextForFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import org.apache.commons.lang3.StringUtils.countMatches
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Paths

@RunWith(JUnit4::class)
class DependenciesHelperTest: AndroidGradleTestCase() {

  @Test
  fun testSimpleAddWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, moduleModel, helper ->
             val updates = helper.addDependency("api", "com.example.libs:lib2:1.0", moduleModel)
             assertThat(updates.size).isEqualTo(2)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("{ group = \"com.example.libs\", name = \"lib2\", version.ref = \"")
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains("api libs.lib2")
           })
  }

  @Test
  fun testAddToBuildscriptWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, moduleModel, helper ->
             val updates = helper.addClasspathDependency("com.example.libs:lib2:1.0")
             assertThat(updates.size).isEqualTo(2)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("{ group = \"com.example.libs\", name = \"lib2\", version.ref = \"")
             assertThat(project.getTextForFile("build.gradle"))
               .contains("classpath libs.lib2")
           })
  }

  @Test
  fun testAddTwoKotlinPlugins() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel!!

             val updates = helper.addPlugin("org.jetbrains.kotlin.android", "1.9.20", false, projectModel, moduleModel)
             assertThat(updates.size).isEqualTo(3)

             val updates2 = helper.addPlugin("org.jetbrains.kotlin.plugin.compose", "1.9.20", false, projectModel, moduleModel)
             assertThat(updates2.size).isEqualTo(3)
           },
           {
             val catalog = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalog).contains("kotlin = \"1.9.20\"")
             assertThat(catalog).contains("kotlin-android = { id = \"org.jetbrains.kotlin.android\", version.ref = \"kotlin\" }")
             assertThat(catalog).contains("kotlin-compose = { id = \"org.jetbrains.kotlin.plugin.compose\", version.ref = \"kotlin\" }")

             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).contains("alias(libs.plugins.kotlin.android) apply false")
             assertThat(projectBuildContent).contains("alias(libs.plugins.kotlin.compose) apply false")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("alias(libs.plugins.kotlin.android)")
             assertThat(buildFileContent).contains("alias(libs.plugins.kotlin.compose)")
           })
  }

  @Test
  fun testSimpleAddNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { _, moduleModel, helper ->
             val updates = helper.addDependency("api", "com.example.libs:lib2:1.0", moduleModel)
             assertThat(updates.size).isEqualTo(1)
           },
           {
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains("api \'com.example.libs:lib2:1.0\'")
           })
  }

  @Test
  fun testAddToClasspathNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { _, moduleModel, helper ->
             val updates = helper.addClasspathDependency("com.example.libs:lib2:1.0")
             assertThat(updates.size).isEqualTo(1)
           },
           {
             assertThat(project.getTextForFile("build.gradle"))
               .contains("classpath \'com.example.libs:lib2:1.0\'")
           })
  }

  @Test
  fun testAddDependencyWithExceptions() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, moduleModel, helper ->
             val updates = helper.addDependency("api",
                                                "com.example.libs:lib2:1.0",
                                                listOf(ArtifactDependencySpecImpl.create("com.example.libs:lib3")!!),
                                                moduleModel,
                                                ExactDependencyMatcher("api", "com.example.libs:lib2:1.0"))
             assertThat(updates.size).isEqualTo(2)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("{ group = \"com.example.libs\", name = \"lib2\", version.ref = \"")
             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("api libs.lib2")
             assertThat(buildFileContent).contains("exclude group: 'com.example.libs', module: 'lib3'")
           })
  }

  @Test
  fun testAddToBuildScriptWithExceptions() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, moduleModel, helper ->
             val updates = helper.addClasspathDependency(
               "com.example.libs:lib2:1.0",
               listOf(ArtifactDependencySpecImpl.create("com.example.libs:lib3")!!),
               ExactDependencyMatcher("classpath", "com.example.libs:lib2:1.0"),
             )
             assertThat(updates.size).isEqualTo(2)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("{ group = \"com.example.libs\", name = \"lib2\", version.ref = \"")
             val buildFileContent = project.getTextForFile("build.gradle")
             assertThat(buildFileContent).contains("classpath libs.lib2")
             assertThat(buildFileContent).contains("exclude group: 'com.example.libs', module: 'lib3'")
           })
  }

  @Test
  fun testAddToBuildScriptWithNoVersion() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, moduleModel, helper ->
             val updates = helper.addDependency("implementation", "com.example.libs:lib2", moduleModel)
             assertThat(updates.size).isEqualTo(2)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("{ group = \"com.example.libs\", name = \"lib2\"")
             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("implementation libs.lib2")
           })
  }

  /**
   * In this test we verify that with GroupName matcher we ignore version when looking for
   * suitable dependency in toml catalog file.
   * So we'll switch to existing junit declaration
   */
  @Test
  fun testAddToBuildScriptWithExistingDependency() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, moduleModel, helper ->
             val updates = helper.addDependency("implementation",
                                                "junit:junit:999",
                                                listOf(),
                                                moduleModel,
                                                GroupNameDependencyMatcher("implementation", "junit:junit:999")
             )
             assertThat(updates.size).isEqualTo(1)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .doesNotContain("= \"999\"")
             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("implementation libs.junit")
           })
  }

  @Test
  fun testSimpleAddNoCatalogWithExceptions() {
    doTest(SIMPLE_APPLICATION,
           { _, moduleModel, helper ->
             val updates = helper.addDependency("api",
                                                "com.example.libs:lib2:1.0",
                                                listOf(ArtifactDependencySpecImpl.create("com.example.libs:lib3")!!),
                                                moduleModel,
                                                ExactDependencyMatcher("api", "com.example.libs:lib2:1.0"))
             assertThat(updates.size).isEqualTo(1)
           },
           {
             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("api \'com.example.libs:lib2:1.0\'")
             assertThat(buildFileContent).contains("exclude group: 'com.example.libs', module: 'lib3'")
           })
  }

  @Test
  fun testSimpleAddPlugin() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val updates = helper.addPlugin("com.example.foo", "10.0", false, projectModel!!, moduleModel)
             assertThat(updates.size).isEqualTo(3)
           },
           {
             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).contains("example-foo = \"10.0\"")
             assertThat(catalogContent).contains("example-foo = { id = \"com.example.foo\", version.ref = \"example-foo\"")

             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).contains("alias(libs.plugins.example.foo) apply false")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("alias(libs.plugins.example.foo)")
           })
  }

  @Test
  fun testSimpleAddPluginNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val updates = helper.addPlugin("com.example.foo", "10.0", false, projectModel!!, moduleModel)
             assertThat(updates.size).isEqualTo(2)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).contains("id 'com.example.foo' version '10.0' apply false")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("apply plugin: 'com.example.foo'")
           })
  }

  @Test
  fun testSimpleAddPluginWithExistingPlugin() {
    val env = BuildEnvironment.getInstance()
    val version = env.gradlePluginVersion
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           {
             FileUtil.appendToFile(
               File(project.basePath, "gradle/libs.versions.toml"),
               "\n[plugins]\nexample = \"com.android.application:${version}\"\n"
             )
           },
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val updates = helper.addPlugin("com.android.application",
                                            version,
                                            false,
                                            projectModel!!,
                                            moduleModel,
                                            IdPluginMatcher("com.android.application"))
             assertThat(updates.size).isEqualTo(0)
           },
           {
             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).doesNotContain("agp = \"${version}\"") // no version
             assertThat(catalogContent).doesNotContain("androidApplication = { id = \"com.android.application\"")

             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("alias(libs.plugins.androidApplication)") // no new alias

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).doesNotContain("alias(libs.plugins.androidApplication)")
           })
  }

  @Test
  fun testAddPluginToModuleWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val changed = helper.addPluginToModule("com.example.foo", "10.0", moduleModel)
             assertThat(changed.size).isEqualTo(2)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("alias(libs.plugins.example.foo)")
           })
  }

  @Test
  fun testAddPluginSettingsWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectModel, buildModel, helper ->
             val plugins = projectModel.projectSettingsModel!!.pluginManagement().plugins()
             val changed = helper.addPlugin("com.example.foo", "10.0", false, plugins, buildModel)
             assertThat(changed.size).isEqualTo(2) // versions.toml and build.gradle
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example.foo")

             val moduleBuildContent = project.getTextForFile("app/build.gradle")
             assertThat(moduleBuildContent).contains("example.foo")

             val settingsBuildContent = project.getTextForFile("settings.gradle")
             assertThat(settingsBuildContent).doesNotContain("example.foo")

             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).contains("com.example.foo")
           })
  }

  @Test
  fun testAddPluginSettings() {
    doTest(SIMPLE_APPLICATION,
           { projectModel, buildModel, helper ->
             val plugins = projectModel.projectSettingsModel!!.pluginManagement().plugins()
             val changed = helper.addPlugin("com.example.foo", "10.0", false, plugins, buildModel)
             assertThat(changed.size).isEqualTo(2)
           },
           {
             val projectBuildContent = project.getTextForFile("app/build.gradle")
             assertThat(projectBuildContent).contains("example.foo")

             val settingsBuildContent = project.getTextForFile("settings.gradle")
             val pluginsBlockContent = getBlockContent(settingsBuildContent, "pluginManagement.plugins")
             assertThat(pluginsBlockContent).contains("'com.example.foo' version '10.0'")
           })
  }

  @Test
  fun testAddPluginToSettingsWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, _, helper ->
             val changed = helper.applySettingsPlugin("com.example.foo", "10.0")
             assertThat(changed.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example")

             val settingsBuildContent = project.getTextForFile("settings.gradle")
             val pluginsBlockContent = getBlockContent(settingsBuildContent, "plugins")
             assertThat(pluginsBlockContent).contains("com.example.foo")
             assertThat(settingsBuildContent).doesNotContain("libs.plugins.example.foo")

             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).doesNotContain("com.example.foo")
           })
  }

  @Test
  fun testAddPluginToSettingsPluginManagementWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, _, helper ->
             val pluginsModel = projectBuildModel.projectSettingsModel?.pluginManagement()?.plugins()
             assertThat(pluginsModel).isNotNull()
             val changed = helper.declarePluginInPluginManagement("com.example.foo", "10.0", null, pluginsModel!!)
             assertThat(changed.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example")

             val settingsBuildContent = project.getTextForFile("settings.gradle")
             val pluginsBlockContent = getBlockContent(settingsBuildContent, "pluginManagement.plugins")
             assertThat(pluginsBlockContent).contains("com.example.foo")
             assertThat(settingsBuildContent).doesNotContain("libs.plugins.example.foo")

             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).doesNotContain("com.example.foo")
           })
  }

  @Test
  fun testAddPluginToModuleWithNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val changed = helper.addPluginToModule("com.example.foo", "10.0", moduleModel)
             assertThat(changed.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("apply plugin: 'com.example.foo'")
           })
  }

  @Test
  fun testAddPluginToSettingsWithNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { _, _, helper ->
             val changed = helper.applySettingsPlugin("com.example.foo", "10.0")
             assertThat(changed.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example")

             val settingsBuildContent = project.getTextForFile("settings.gradle")
             val pluginsBlockContent = getBlockContent(settingsBuildContent, "plugins")
             assertThat(pluginsBlockContent).contains("id 'com.example.foo' version '10.0'")
             assertThat(settingsBuildContent).doesNotContain("libs.plugins.example.foo")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).doesNotContain("com.example.foo")
           })
  }

  @Test
  fun testAddPluginToSettingsPluginManagementWithNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, _, helper ->
             val pluginsModel = projectBuildModel.projectSettingsModel?.pluginManagement()?.plugins()
             assertThat(pluginsModel).isNotNull()
             val changed = helper.declarePluginInPluginManagement("com.example.foo", "10.0", null, pluginsModel!!)
             assertThat(changed.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example")

             val settingsBuildContent = project.getTextForFile("settings.gradle")
             val pluginsBlockContent = getBlockContent(settingsBuildContent, "pluginManagement.plugins")
             assertThat(pluginsBlockContent).contains("id 'com.example.foo' version '10.0'")
             assertThat(settingsBuildContent).doesNotContain("libs.plugins.example.foo")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).doesNotContain("com.example.foo")
           })
  }

  @Test
  fun testSimpleAddWithCatalogIgnoreExistingDeclaration() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val catalogModel = projectBuildModel.versionCatalogsModel.getVersionCatalogModel("libs")!!
             catalogModel.pluginDeclarations().addDeclaration("libsPlugin", "com.example.libs:0.5")
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             helper.addPlugin("com.example.libs", "1.0", false, projectModel!!, moduleModel, FalsePluginMatcher())
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("exampleLibs = { id = \"com.example.libs\", version.ref = \"")
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains("alias(libs.plugins.exampleLibs)")
             assertThat(project.getTextForFile("build.gradle"))
               .contains("alias(libs.plugins.exampleLibs) apply false")
           })
  }

  @Test
  fun testAddDuplicatedPlatformDependencyWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val matcher = GroupNameDependencyMatcher("implementation", "com.google.protobuf:protobuf-bom:3.21.8")

             helper.addPlatformDependency("implementation",
                                  "com.google.protobuf:protobuf-bom:3.21.8",
                                  false,
                                  moduleModel,
                                  matcher)
             helper.addPlatformDependency("implementation",
                                  "com.google.protobuf:protobuf-bom:3.21.8",
                                  false,
                                  moduleModel,
                                  matcher)
           },
           {
             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(countMatches(catalogContent, "= \"3.21.8\"")).isEqualTo(1)
             assertThat(countMatches(catalogContent,"= { group = \"com.google.protobuf\", name = \"protobuf-bom\"")).isEqualTo(1)

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(countMatches(buildFileContent,"implementation platform(libs.protobuf.bom)")).isEqualTo(1)
           })
  }

  @Test
  fun testAddDuplicatedPlatformDependencyNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val dependency = "com.google.protobuf:protobuf-bom:3.21.8"
             val matcher = GroupNameDependencyMatcher("implementation", dependency)

             helper.addPlatformDependency("implementation", dependency, false, moduleModel, matcher)
             helper.addPlatformDependency("implementation", dependency, false, moduleModel, matcher)
           },
           {
             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(countMatches(buildFileContent,"implementation platform('com.google.protobuf:protobuf-bom:3.21.8')")).isEqualTo(1)
           })
  }

  @Test
  fun testAddDuplicatedDependencyWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val matcher = GroupNameDependencyMatcher("api", "com.example.libs:lib2:1.0")

             helper.addDependency("api", "com.example.libs:lib2:1.0", listOf(), moduleModel, matcher)
             helper.addDependency("api", "com.example.libs:lib2:1.0", listOf(), moduleModel, matcher)
           },
           {
             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(countMatches(catalogContent, "{ group = \"com.example.libs\", name = \"lib2\", version.ref = \"")).isEqualTo(1)

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(countMatches(buildFileContent,"api libs.lib2")).isEqualTo(1)
           })
  }

  @Test
  fun testAddDuplicatedDependencyNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val matcher = GroupNameDependencyMatcher("api", "com.example.libs:lib2:1.0")

             helper.addDependency("api", "com.example.libs:lib2:1.0", listOf(), moduleModel, matcher)
             helper.addDependency("api", "com.example.libs:lib2:1.0", listOf(), moduleModel, matcher)
           },
           {
             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(countMatches(buildFileContent,"api 'com.example.libs:lib2:1.0'")).isEqualTo(1)
           })
  }

  @Test
  fun testAddClasspathDuplicatedDependencyWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, _, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val matcher = GroupNameDependencyMatcher("classpath", "com.example.libs:lib2:1.0")

             helper.addClasspathDependency("com.example.libs:lib2:1.0", listOf(), matcher)
             helper.addClasspathDependency("com.example.libs:lib2:1.0", listOf(), matcher)
           },
           {
             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(countMatches(catalogContent,"{ group = \"com.example.libs\", name = \"lib2\", version.ref = \"")).isEqualTo(1)

             val buildFileContent = project.getTextForFile("build.gradle")
             assertThat(countMatches(buildFileContent,"classpath libs.lib2")).isEqualTo(1)
           })
  }

  @Test
  fun testAddClasspathDuplicatedDependencyNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val matcher = GroupNameDependencyMatcher("classpath", "com.example.libs:lib2:1.0")

             helper.addClasspathDependency("com.example.libs:lib2:1.0", listOf(), matcher)
             helper.addClasspathDependency("com.example.libs:lib2:1.0", listOf(), matcher)
           },
           {
             val buildFileContent = project.getTextForFile("build.gradle")
             assertThat(countMatches(buildFileContent,"classpath 'com.example.libs:lib2:1.0'")).isEqualTo(1)
           })
  }

  @Test
  fun testSimpleAddPluginNoCatalogWithExistingPlugin() {
    val env = BuildEnvironment.getInstance()
    val version = env.gradlePluginVersion
    doTest(SIMPLE_APPLICATION_PLUGINS_DSL,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val changed = helper.addPlugin("com.android.application",
                              version,
                              false,
                              projectModel!!,
                              moduleModel,
                              IdPluginMatcher("com.android.application"))
             assertThat(changed.size).isEqualTo(0)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             // once test is imported it updates agp version and adds updates as comments on the bottom of file
             // this comment affects simple counter with countMatches
             val regex = "\\R\\s*id 'com.android.application' version '${version}' apply false".toRegex()
             assertThat(regex.findAll(projectBuildContent).toList().size).isEqualTo(1)

             val buildFileContent = project.getTextForFile("app/build.gradle")

             assertThat(countMatches(buildFileContent,"id 'com.android.application'")).isEqualTo(1)
           })
  }

  @Test
  fun testSmartAddPluginNoCatalog() {

    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()

             val changed = helper.addPlugin("com.google.gms.google-services",
                                            "com.google.gms:google-services:4.3.14",
                                            listOf(moduleModel))
             assertThat(changed.size).isEqualTo(2)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             val regex = "\\R\\s*classpath 'com.google.gms:google-services:4.3.14'".toRegex()
             assertThat(regex.findAll(projectBuildContent).toList().size).isEqualTo(1)

             assertThat(project.doesFileExists("gradle/libs.versions.toml")).isFalse()
             assertThat(projectBuildContent).doesNotContain("plugins")
             assertThat(project.getTextForFile("settings.gradle")).doesNotContain("plugins")

             val buildFileContent = project.getTextForFile("app/build.gradle")

             assertThat(countMatches(buildFileContent, "apply plugin: 'com.google.gms.google-services'")).isEqualTo(1)
           })
  }

  @Test
  fun testSmartAddPluginToPluginManagement() {
    doTest(SIMPLE_APPLICATION,
           {
             val file = File(project.basePath, "settings.gradle")
             val text = file.readText()
             FileUtil.writeToFile(
               file,
               """
                pluginManagement {
                  plugins {
                  }
                }
                """.trimIndent() + "\n" + text)
           },
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()

             val changed = helper.addPlugin("com.google.gms.google-services",
                                            "com.google.gms:google-services:4.3.14",
                                            listOf(moduleModel))
             assertThat(changed.size).isEqualTo(2)
           },
           {
             val settingsContent = project.getTextForFile("settings.gradle")
             val regex = "plugins \\{\\n\\s*id 'com.google.gms.google-services'".toRegex()
             assertThat(regex.findAll(settingsContent).toList().size).isEqualTo(1)

             assertThat(project.doesFileExists("gradle/libs.versions.toml")).isFalse()
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("plugins")
             assertThat(projectBuildContent).doesNotContain("classpath 'com.google.gms:google-services:4.3.14'")

             val buildFileContent = project.getTextForFile("app/build.gradle")

             assertThat(countMatches(buildFileContent,"apply plugin: 'com.google.gms.google-services'")).isEqualTo(1)
           })
  }

  /**
   * Test case when addPlugin is called for an existing plugin (with version catalog)
   */
  @Test
  fun testSmartAddPluginWithCatalogWithExistingPlugin() {
    val env = BuildEnvironment.getInstance()
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           {
             val settingsFile = File(project.basePath, "settings.gradle")
             val settingsFileText = settingsFile.readText()
             FileUtil.writeToFile(
               settingsFile,
               settingsFileText.replace("pluginManagement {", "pluginManagement {\n  plugins {}")
             )
           },
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()

             val changed =
               helper.addPlugin(
                 "com.android.application",
                 "com.android.tools.build:gradle:${env.gradlePluginVersion}",
                 listOf(moduleModel)
               )
             assertThat(changed.size).isEqualTo(0)
           },
           {
             val settingsContent = project.getTextForFile("settings.gradle")
             assertThat(settingsContent).isNotEmpty()
             assertThat(settingsContent).doesNotContain("com.android.application")

             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).isNotEmpty()
             assertThat(catalogContent).doesNotContain("com.android.application")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(countMatches(buildFileContent,"'com.android.application'")).isEqualTo(1)
           })
  }

  /**
   * Test case when addPlugin is called for an existing plugin (without version catalog)
   */
  @Test
  fun testSmartAddPluginNoCatalogWithExistingPlugin() {
    val env = BuildEnvironment.getInstance()
    doTest(SIMPLE_APPLICATION,
           {
             val settingsFile = File(project.basePath, "settings.gradle")
             val settingsFileText = settingsFile.readText()
             FileUtil.writeToFile(
               settingsFile,
               """
                 pluginManagement {
                   plugins {
                   }
                 }
                 """.trimIndent() + "\n" + settingsFileText
             )
           },
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()

             val changed =
               helper.addPlugin(
                 "com.android.application",
                 "com.android.tools.build:gradle:${env.gradlePluginVersion}",
                 listOf(moduleModel)
               )
             assertThat(changed.size).isEqualTo(0)
           },
           {
             val settingsContent = project.getTextForFile("settings.gradle")
             assertThat(settingsContent).isNotEmpty()
             assertThat(settingsContent).doesNotContain("com.android.application")

             assertThat(project.doesFileExists("gradle/libs.versions.toml")).isFalse()

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(countMatches(buildFileContent,"apply plugin: 'com.android.application'")).isEqualTo(1)
           })
  }

  @Test
  fun testSmartAddPluginNoCatalogPluginsBlock() {
    doTest(MIGRATE_BUILD_CONFIG,
           { _, moduleModel, helper ->
             val changed = helper.addPlugin("com.google.gms.google-services",
                                            "com.google.gms:google-services:4.3.14",
                                            listOf(moduleModel))
             assertThat(changed.size).isEqualTo(2)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             val regex = "id 'com.google.gms.google-services' version '4.3.14' apply false".toRegex()
             assertThat(regex.findAll(projectBuildContent).toList().size).isEqualTo(1)

             assertThat(project.doesFileExists("gradle/libs.versions.toml")).isFalse()
             assertThat(projectBuildContent).doesNotContain("classpath")

             // root project plugins block
             val moduleBuildContent = project.getTextForFile("app/build.gradle")
             val regex2 = "id 'com.google.gms.google-services'".toRegex()
             assertThat(regex2.findAll(moduleBuildContent).toList().size).isEqualTo(1)
           })
  }

  @Test
  fun testSmartAddPluginWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, moduleModel, helper ->
             val changed = helper.addPlugin("com.google.gms.google-services",
                                            "com.google.gms:google-services:4.3.14",
                                            listOf(moduleModel))
             assertThat(changed.size).isEqualTo(3)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("google-gms-google-services = { id = \"com.google.gms.google-services\", version.ref = \"")
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains("alias(libs.plugins.google.gms.google.services)")

             val projectBuildContent = project.getTextForFile("build.gradle")

             assertThat(projectBuildContent)
               .contains("alias(libs.plugins.google.gms.google.services) apply false")

             assertThat(project.getTextForFile("settings.gradle")).doesNotContain("plugins")
             assertThat(projectBuildContent).doesNotContain("dependencies")
           })
  }


  @Test
  fun testSmartAddPluginToNewProject() {
    doTest(MINIMAL_CATALOG_APPLICATION,
           { _, moduleModel, helper ->
             val changed = helper.addPlugin("com.google.gms.google-services",
                                            "com.google.gms:google-services:4.3.14",
                                            listOf(moduleModel))
             assertThat(changed.size).isEqualTo(3)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("google-gms-google-services = { id = \"com.google.gms.google-services\", version.ref = \"")
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains("alias(libs.plugins.google.gms.google.services)")

             val projectBuildContent = project.getTextForFile("build.gradle")

             assertThat(projectBuildContent)
               .contains("alias(libs.plugins.google.gms.google.services) apply false")

             assertThat(project.getTextForFile("settings.gradle")).doesNotContain("plugins")
             assertThat(projectBuildContent).doesNotContain("dependencies") // no classpath
           })
  }

  private fun doTest(projectPath: String,
                     change: (projectBuildModel: ProjectBuildModel, model: GradleBuildModel, helper: DependenciesInserter) -> Unit,
                     assert: () -> Unit) {
    doTest(projectPath, {}, change, assert)
  }

  private fun doTest(projectPath: String,
                     updateFiles: () -> Unit,
                     change: (projectBuildModel: ProjectBuildModel, model: GradleBuildModel, helper: DependenciesInserter) -> Unit,
                     assert: () -> Unit) {
    prepareProjectForImport(projectPath)
    updateFiles()
    VfsUtil.markDirtyAndRefresh(false, true, true, findFileByIoFile(projectFolderPath, true));
    importProject()
    prepareProjectForTest(project, null)
    myFixture.allowTreeAccessForAllFiles()

    val projectBuildModel = ProjectBuildModel.get(project)
    val moduleModel: GradleBuildModel? = projectBuildModel.getModuleBuildModel(project.findModule("app"))
    assertThat(moduleModel).isNotNull()
    val helper = DependenciesHelper.withModel(projectBuildModel)
    change.invoke(projectBuildModel, moduleModel!!, helper)
    WriteCommandAction.runWriteCommandAction(project) {
      projectBuildModel.applyChanges()
      moduleModel.applyChanges()
    }
    assert.invoke()
  }

  private fun isRootElement(string: String, elementPosition: Int): Boolean {
    var counter = 0
    for (pos in 0 until elementPosition) {
      when (string[pos]) {
        '{' -> counter += 1
        '}' -> counter -= 1
        else -> Unit
      }
      assertThat(counter).isGreaterThan(-1)
    }
    return counter == 0
  }

  /**
   * Returns content between curly braces `plugins{ ... }``
   */
  private fun getBlockContent(string: String, blockStart: Int): String? {
    var start = -1
    var counter = 0
    for (pos in blockStart until string.length) {
      when (string[pos]) {
        '{' -> {
          if(start == -1) start = pos
          counter += 1
        }
        '}' -> counter -= 1
        else -> Unit
      }
      if (counter == 0 && start >= 0) return string.substring(start + 1, pos - 1)
    }
    return null
  }

  /**
   * Method returns content of block that we specify in path - for example `pluginManagement.plugins`
   * It does not handle block duplication.
   */
  private fun getBlockContent(text: String, path: String): String {
    val elements = path.split(".")
    assert(elements.isNotEmpty()) { "Path must be formatted as dot separated path `pluginManagement.plugins`" }
    fun snippet(string: String, element: String): String? {
      val blockNamePosition = "$element[ \\t\\n\\{]".toRegex().find(string)?.range?.start
      if (blockNamePosition == null) {
        fail("Cannot find $element")
        return null
      }
      if (blockNamePosition >= 0)
        if (isRootElement(string, blockNamePosition)) {
          return getBlockContent(string, blockNamePosition)
        }
        else return snippet(string.substring(blockNamePosition + element.length), element)
      return null
    }

    var currentSnippet = text
    for (element in elements) {
      snippet(currentSnippet, element)?.let { currentSnippet = it } ?: fail("Cannot get block content for element $element in $path for file: `$text`")
    }
    return currentSnippet
  }

  private fun Project.doesFileExists(relativePath:String) =
    VfsUtil.findFile(Paths.get(basePath, relativePath), false)?.exists() ?: false

}