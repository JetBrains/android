/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.sync.internal.dump
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.projectsystem.getAndroidTestModule
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.assertAreEqualToSnapshots
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.saveAndDump
import com.google.common.truth.Expect
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

@RunsInEdt
class SyncScenariosIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun testPsdDependencyUpgradeLibraryModule() {
    class ModuleSnapshots(val mainMain: String, val mainAndroidTest: String, val modulePlusMain: String)

    fun Project.dumpTestedModules(): ModuleSnapshots =
      ModuleSnapshots(
        mainMain = dumpModule(":mainModule", Module::getMainModule),
        mainAndroidTest = dumpModule(":mainModule", Module::getAndroidTestModule),
        modulePlusMain = dumpModule(":modulePlus", Module::getMainModule),
      )

    prepareGradleProject(TestProjectToSnapshotPaths.PSD_DEPENDENCY, "project")
    openPreparedProject("project") { project ->
      val beforeLibUpgrade = project.dumpTestedModules()
      PsProjectImpl(project).let { projectModel ->
        projectModel
          .findModuleByGradlePath(":modulePlus")!!
          .dependencies
          .findLibraryDependencies("com.example.libs", "lib1")
          .forEach { it.version = "1.0".asParsed() }
        projectModel
          .findModuleByGradlePath(":mainModule")!!
          .dependencies
          .findLibraryDependencies("com.example.libs", "lib1")
          .forEach { it.version = "0.9.1".asParsed() }
        projectModel
          .findModuleByGradlePath(":mainModule")!!
          .dependencies
          .findLibraryDependencies("com.example.jlib", "lib3")
          .single().version = "0.9.1".asParsed()
        projectModel.applyChanges()
      }
      project.requestSyncAndWait()
      val afterLibUpgrade = project.dumpTestedModules()

      fun String.replaceVersions(from: String, to: String): String {
        return replace(Regex("(com\\.example\\.j?libs?:lib.:)$from")) { it.groupValues[1] + to }
      }

      // Note: in fact the test downgrades the version in `:mainModule`.
      expect.that(afterLibUpgrade.mainMain)
        .isEqualTo(beforeLibUpgrade.mainMain.replaceVersions(from = "1\\.0", to = "0.9.1"))
      expect.that(afterLibUpgrade.mainAndroidTest)
        .isEqualTo(beforeLibUpgrade.mainAndroidTest.replaceVersions(from = "1\\.0", to = "0.9.1"))

      expect.that(afterLibUpgrade.modulePlusMain)
        .isEqualTo(beforeLibUpgrade.modulePlusMain.replaceVersions(from = "0\\.9\\.1", to = "1.0"))
    }
  }

  @Test
  fun testPsdSampleRenamingModule() {
    prepareGradleProject(TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY, "project")
    openPreparedProject("project") { project ->
      val beforeRename = project.dumpModule(":nested1:deep") { getMainModule() }
      expect.that(project.gradleModule(":container1:deep")).isNull()
      PsProjectImpl(project).let { projectModel ->
        projectModel.removeModule(":nested1")
        projectModel.removeModule(":nested1:deep")
        with(projectModel.parsedModel.projectSettingsModel!!) {
          addModulePath(":container1")
          addModulePath(":container1:deep")
        }
        projectModel.applyChanges()
      }
      WriteAction.run<Throwable> {
        project.guessProjectDir()!!.findFileByRelativePath("nested1")!!.rename("test", "container1")
      }
      ApplicationManager.getApplication().saveAll()
      project.requestSyncAndWait()
      expect.that(project.gradleModule(":nested1:deep")).isNull()
      val afterRename = project.dumpModule(":container1:deep") { getMainModule() }
      // Do not use expect since IDEA does not show the diff UI in this case.
      assertThat(afterRename).isEqualTo(beforeRename.replace("nested1", "container1"))
    }
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))
}

private fun Project.dumpModule(moduleName: String, sourcesetModule: Module.() -> Module?) =
      saveAndDump { project, dumper ->
        project.gradleModule(moduleName)!!.sourcesetModule()?.also {
          dumper.dump(it)
        }
      }

private fun <T : Any> T.asParsed() = ParsedValue.Set.Parsed(this, DslText.Literal)
