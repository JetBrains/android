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

import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.resolveIn
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.requestSyncAndWait
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import com.intellij.util.text.nullize
import org.jetbrains.android.AndroidTestBase
import org.junit.Rule
import org.junit.Test
import java.io.File

class GradleProjectPathIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @Test
  fun gradleProjectPaths() {
    val root = prepareGradleProject(TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES, "project")
    openPreparedProject("project") { project ->
      assertThat(dumpModuleToGradlePathMapping(project, root)).isEqualTo(
        """
            ==> :
            .aarWrapperLib ==> :aarWrapperLib
            .app ==> :app
            .app.androidTest ==> :app/ANDROID_TEST
            .app.main ==> :app/MAIN
            .app.unitTest ==> :app/UNIT_TEST
            .feature-a ==> :feature-a
            .feature-a.androidTest ==> :feature-a/ANDROID_TEST
            .feature-a.main ==> :feature-a/MAIN
            .feature-a.unitTest ==> :feature-a/UNIT_TEST
            .jarWrapperLib ==> :jarWrapperLib
            .javaLibrary ==> :javaLibrary
            .javaLibrary.main ==> :javaLibrary/MAIN
            .javaLibrary.test ==> :javaLibrary/test
            .javaLibrary.testEnv ==> :javaLibrary/testEnv
            .lib ==> :lib
            .lib.androidTest ==> :lib/ANDROID_TEST
            .lib.main ==> :lib/MAIN
            .lib.unitTest ==> :lib/UNIT_TEST
            .network-kmm ==> :network-kmm
            .network-kmm.commonMain ==> :network-kmm/commonMain
            .network-kmm.commonTest ==> :network-kmm/commonTest
            .network-kmm.jvmMain ==> :network-kmm/jvmMain
            .network-kmm.jvmTest ==> :network-kmm/jvmTest
        """.trimIndent()
      )
      assertThatProjectPathsCanBeResolved(project)
    }
  }

  @Test
  fun gradleProjectPaths_inComposites() {
    val root = prepareGradleProject(TestProjectToSnapshotPaths.COMPOSITE_BUILD, "project")
    openPreparedProject("project") { project ->
      assertThat(dumpModuleToGradlePathMapping(project, root)).isEqualTo(
        """
            ==> :
            .app ==> :app
            .app.androidTest ==> :app/ANDROID_TEST
            .app.main ==> :app/MAIN
            .app.unitTest ==> :app/UNIT_TEST
            .lib ==> :lib
            .lib.androidTest ==> :lib/ANDROID_TEST
            .lib.main ==> :lib/MAIN
            .lib.unitTest ==> :lib/UNIT_TEST
            TestCompositeLib1 ==> [TestCompositeLib1]:
            TestCompositeLib1.app ==> [TestCompositeLib1]:app
            TestCompositeLib1.app.androidTest ==> [TestCompositeLib1]:app/ANDROID_TEST
            TestCompositeLib1.app.main ==> [TestCompositeLib1]:app/MAIN
            TestCompositeLib1.app.unitTest ==> [TestCompositeLib1]:app/UNIT_TEST
            TestCompositeLib1.lib ==> [TestCompositeLib1]:lib
            TestCompositeLib1.lib.androidTest ==> [TestCompositeLib1]:lib/ANDROID_TEST
            TestCompositeLib1.lib.main ==> [TestCompositeLib1]:lib/MAIN
            TestCompositeLib1.lib.unitTest ==> [TestCompositeLib1]:lib/UNIT_TEST
            TestCompositeLib3 ==> [TestCompositeLib3]:
            TestCompositeLib3.app ==> [TestCompositeLib3]:app
            TestCompositeLib3.app.androidTest ==> [TestCompositeLib3]:app/ANDROID_TEST
            TestCompositeLib3.app.main ==> [TestCompositeLib3]:app/MAIN
            TestCompositeLib3.app.unitTest ==> [TestCompositeLib3]:app/UNIT_TEST
            TestCompositeLib3.lib ==> [TestCompositeLib3]:lib
            TestCompositeLib3.lib.androidTest ==> [TestCompositeLib3]:lib/ANDROID_TEST
            TestCompositeLib3.lib.main ==> [TestCompositeLib3]:lib/MAIN
            TestCompositeLib3.lib.unitTest ==> [TestCompositeLib3]:lib/UNIT_TEST
            com.test.compositeNest3.compositeNest ==> [TestCompositeLib3/TestCompositeLibNested_3]:
            com.test.compositeNest3.compositeNest.main ==> [TestCompositeLib3/TestCompositeLibNested_3]/MAIN
            com.test.compositeNest3.compositeNest.test ==> [TestCompositeLib3/TestCompositeLibNested_3]/test
            composite2 ==> [TestCompositeLib2]:
            composite2.main ==> [TestCompositeLib2]/MAIN
            composite2.test ==> [TestCompositeLib2]/test
            composite4 ==> [TestCompositeLib4]:
            composite4.main ==> [TestCompositeLib4]/MAIN
            composite4.test ==> [TestCompositeLib4]/test
            compositeNest ==> [TestCompositeLib1/TestCompositeLibNested_1]:
            compositeNest.main ==> [TestCompositeLib1/TestCompositeLibNested_1]/MAIN
            compositeNest.test ==> [TestCompositeLib1/TestCompositeLibNested_1]/test
        """.trimIndent()
      )
      assertThatProjectPathsCanBeResolved(project)
    }
  }

  @Test
  fun updatesResolutionCache() {
    val root = prepareGradleProject(TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES, "project")
    openPreparedProject("project") { project ->
      assertThatProjectPathsCanBeResolved(project)

      // Move :app to :app:main and thus cause some modules to be re-created or at least new modules to be created. If the project wide
      // cache is not reset the expectation below fails.
      moveAppToAppMain(root)
      project.requestSyncAndWait()

      assertThatProjectPathsCanBeResolved(project)
    }
  }

  private fun moveAppToAppMain(root: File) {
    root.resolve("app").renameTo(root.resolve("app1"))
    root.resolve("app").mkdir()
    root.resolve("app1").renameTo(root.resolve("app").resolve("main"))
    root.resolve("settings.gradle").let { settingsFile ->
      settingsFile.writeText(
        settingsFile.readText().replace("':app'", "':app:main'")
      )
    }
  }

  private fun dumpModuleToGradlePathMapping(project: Project, root: File) =
    ModuleManager.getInstance(project).modules.map { it.name to it.getGradleProjectPath() }
      .map { (key, value) ->
        val moduleName = key.removePrefix(project.name)
        "$moduleName ==>${
          value?.let {
            val buildId = value.buildRoot.let(::File).relativeToOrSelf(root).path.nullize()?.let { "[$it]" }.orEmpty()
            val gradlePath = value.path
            val sourceSet = (value as? GradleSourceSetProjectPath)?.sourceSet?.let { "/$it" }.orEmpty()
            " ${buildId}$gradlePath$sourceSet"
          } ?: ""
        }"
      }
      .sorted()
      .joinToString("\n")
      .trim()

  private fun assertThatProjectPathsCanBeResolved(project: Project) {
    val pathMap = ModuleManager.getInstance(project).modules.map { it to it.getGradleProjectPath() }
    pathMap.forEach { (module, gradlePath) ->
      if (gradlePath != null) assertThat(gradlePath.resolveIn(project)).isSameAs(module)
    }
  }

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))
}
