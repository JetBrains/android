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
package com.android.tools.idea.gradle.dependencies.runsGradleDependencies

import com.android.tools.idea.flags.DeclarativeStudioSupport
import com.android.tools.idea.gradle.dcl.lang.ide.DeclarativeIdeSupport
import com.android.tools.idea.gradle.dependencies.DependenciesHelper
import com.android.tools.idea.gradle.dependencies.DependenciesInserter
import com.android.tools.idea.gradle.dependencies.ExactDependencyMatcher
import com.android.tools.idea.gradle.dependencies.GroupNameDependencyMatcher
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_DECLARATIVE
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.getTextForFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import org.apache.commons.lang3.StringUtils.countMatches
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

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
  fun testSimpleAddDeclarative() {
    doDeclarativeTest(SIMPLE_APPLICATION_DECLARATIVE,
           { _, moduleModel, helper ->
             val updates = helper.addDependency("api", "com.example.libs:lib2:1.0", moduleModel)
             assertThat(updates.size).isEqualTo(1)
           },
           {
             val buildFile = project.getTextForFile("app/build.gradle.dcl")
             val dependencies = getBlockContent(buildFile, "androidApp.dependenciesDcl")
             assertThat(dependencies).contains("api(\"com.example.libs:lib2:1.0\")")
             // check that it's existing dependenciesDcl block - not new one
             assertThat(dependencies).contains("api(\"com.google.guava:guava:19.0\")")
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

  private fun doDeclarativeTest(projectPath: String,
                                change: (projectBuildModel: ProjectBuildModel, model: GradleBuildModel, helper: DependenciesInserter) -> Unit,
                                assert: () -> Unit) {
    DeclarativeStudioSupport.override(true)
    DeclarativeIdeSupport.override(true)
    try {
      doTest(projectPath, {}, change, assert, true)
    }
    finally {
      DeclarativeStudioSupport.clearOverride()
      DeclarativeIdeSupport.clearOverride()
    }
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
    doTest(projectPath, updateFiles, change, assert, true)
  }

  private fun doTest(projectPath: String,
                     updateFiles: () -> Unit,
                     change: (projectBuildModel: ProjectBuildModel, model: GradleBuildModel, helper: DependenciesInserter) -> Unit,
                     assert: () -> Unit,
                     setupGradleSnapshot: Boolean) {
    prepareProjectForImport(projectPath)
    updateFiles()
    VfsUtil.markDirtyAndRefresh(false, true, true, findFileByIoFile(projectFolderPath, true))
    if(setupGradleSnapshot) setupGradleSnapshotToWrapper(project)
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
}