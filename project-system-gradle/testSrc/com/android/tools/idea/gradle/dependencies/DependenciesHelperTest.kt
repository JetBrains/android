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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KOIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dependencies

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.getTextForFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DependenciesHelperTest: AndroidGradleTestCase() {

  @Test
  fun testSimpleAddWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, moduleModel, helper ->
             helper.addDependency("api", "com.example.libs:lib2:1.0", moduleModel)
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
             helper.addClasspathDependency("com.example.libs:lib2:1.0")
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("{ group = \"com.example.libs\", name = \"lib2\", version.ref = \"")
             assertThat(project.getTextForFile("build.gradle"))
               .contains("classpath libs.lib2")
           })
  }

  @Test
  fun testSimpleAddNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { _, moduleModel, helper ->
             helper.addDependency("api", "com.example.libs:lib2:1.0", moduleModel)
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
             helper.addClasspathDependency("com.example.libs:lib2:1.0")
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
             helper.addDependency("api",
                                  "com.example.libs:lib2:1.0",
                                  listOf(ArtifactDependencySpecImpl.create("com.example.libs:lib3")!!),
                                  ExactDependencyMatcher("com.example.libs:lib2:1.0"),
                                  moduleModel)
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
             helper.addClasspathDependency("com.example.libs:lib2:1.0",
                                           listOf(ArtifactDependencySpecImpl.create("com.example.libs:lib3")!!),
                                           ExactDependencyMatcher("com.example.libs:lib2:1.0"),
             )
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
             helper.addDependency("implementation", "com.example.libs:lib2", moduleModel)
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
             helper.addDependency("implementation",
                                  "junit:junit:999",
                                  listOf(),
                                  GroupNameDependencyMatcher("junit:junit:999"),
                                  moduleModel)
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
             helper.addDependency("api",
                                  "com.example.libs:lib2:1.0",
                                  listOf(ArtifactDependencySpecImpl.create("com.example.libs:lib3")!!),
                                  ExactDependencyMatcher("com.example.libs:lib2:1.0"),
                                  moduleModel)
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
             helper.addPlugin("com.example.foo", "10.0", false, IdPluginMatcher("pluginId"), projectModel!!, moduleModel)
           },
           {
             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).contains("example-foo = \"10.0\"")
             assertThat(catalogContent).contains("exampleFoo = { id = \"com.example.foo\", version.ref = \"example-foo\"")

             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).contains("alias(libs.plugins.exampleFoo) apply false")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("alias(libs.plugins.exampleFoo)")
           })
  }

  @Test
  fun testSimpleAddPluginNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             helper.addPlugin("com.example.foo", "10.0", false, IdPluginMatcher("pluginId"), projectModel!!, moduleModel)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).contains("id 'com.example.foo' version '10.0' apply false")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("apply plugin: 'com.example.foo'")
           })
  }

  private fun doTest(projectPath: String,
                     change: (projectBuildModel: ProjectBuildModel, model: GradleBuildModel, helper: DependenciesHelper) -> Unit,
                     assert: () -> Unit) {
    loadProject(projectPath)
    val projectBuildModel = ProjectBuildModel.get(project)
    val moduleModel: GradleBuildModel? = projectBuildModel.getModuleBuildModel(project.findModule("app"))
    assertThat(moduleModel).isNotNull()
    val helper = DependenciesHelper(projectBuildModel)
    change.invoke(projectBuildModel, moduleModel!!, helper)
    WriteCommandAction.runWriteCommandAction(project) {
      projectBuildModel.applyChanges()
      moduleModel.applyChanges()
    }
    assert.invoke()
  }

}