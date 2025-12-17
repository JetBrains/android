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
package com.android.tools.idea.gradle.project.sync.runsGradle

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.android.tools.idea.projectsystem.gradle.getGradleIdentityPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.resolveIn
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.requestSyncAndWait
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RuleChain
import com.intellij.util.text.nullize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File

@RunWith(Parameterized::class)
class GradleProjectPathIntegrationTest(private val phasedSync: Boolean) {

  companion object {
    @get:Parameters(name="phased:{0}")
    @get:JvmStatic
    val phasedSyncValues = listOf(true, false)
  }

  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val rule = RuleChain(
    FlagRule(StudioFlags.PHASED_SYNC_ENABLED, phasedSync),
    FlagRule(StudioFlags.PHASED_SYNC_BRIDGE_DATA_SERVICE_DISABLED, phasedSync),
    projectRule,
  )

  @Test
  fun gradleProjectPaths() {
    val preparedProject = projectRule.prepareTestProject(TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES)
    preparedProject.open { project ->
      assertThat(dumpModuleToGradlePathMapping(project, preparedProject.root)).isEqualTo(
        """
            ==> :
            .aarWrapperLib ==> :aarWrapperLib
            .app ==> :app
            .app.androidTest ==> :app/ANDROID_TEST
            .app.main ==> :app/MAIN
            .app.unitTest ==> :app/UNIT_TEST
            .common ==> :common
            .common.commonMain ==> :common/commonMain
            .common.commonTest ==> :common/commonTest
            .common.jvmMain ==> :common/jvmMain
            .common.jvmTest ==> :common/jvmTest
            .desktop ==> :desktop
            .desktop.main ==> :desktop/MAIN
            .desktop.test ==> :desktop/test
            .feature-a ==> :feature-a
            .feature-a.androidTest ==> :feature-a/ANDROID_TEST
            .feature-a.main ==> :feature-a/MAIN
            .feature-a.unitTest ==> :feature-a/UNIT_TEST
            .feature-b ==> :feature-b
            .feature-b.androidTest ==> :feature-b/ANDROID_TEST
            .feature-b.main ==> :feature-b/MAIN
            .feature-b.unitTest ==> :feature-b/UNIT_TEST
            .jarWrapperLib ==> :jarWrapperLib
            .javaLibrary ==> :javaLibrary
            .javaLibrary.main ==> :javaLibrary/MAIN
            .javaLibrary.test ==> :javaLibrary/test
            .javaLibrary.testEnv ==> :javaLibrary/testEnv
            .kmp-java ==> :kmp-java
            .kmp-java.sample ==> :kmp-java:sample
            .kmp-java.sample-test ==> :kmp-java:sample-test
            .kmp-java.sample-test.androidTest ==> :kmp-java:sample-test/ANDROID_TEST
            .kmp-java.sample-test.main ==> :kmp-java:sample-test/MAIN
            .kmp-java.sample-test.unitTest ==> :kmp-java:sample-test/UNIT_TEST
            .kmp-java.sample.commonMain ==> :kmp-java:sample/commonMain
            .kmp-java.sample.commonTest ==> :kmp-java:sample/commonTest
            .kmp-java.sample.jvmMain ==> :kmp-java:sample/jvmMain
            .kmp-java.sample.jvmTest ==> :kmp-java:sample/jvmTest
            .kmp-java.sample.main ==> :kmp-java:sample/MAIN
            .kmp-java.sample.test ==> :kmp-java:sample/test
            .lib ==> :lib
            .lib.androidTest ==> :lib/ANDROID_TEST
            .lib.main ==> :lib/MAIN
            .lib.unitTest ==> :lib/UNIT_TEST
        """.trimIndent()
      )
      assertThatProjectPathsCanBeResolved(project)
    }
  }

  @Test
  fun gradleProjectPaths_inComposites() {
    val preparedProject = projectRule.prepareTestProject(TestProject.COMPOSITE_BUILD)
    preparedProject.open { project ->
      if (phasedSync) {
        assertThat(dumpModuleToGradlePathMapping(project, preparedProject.root)).isEqualTo(
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
            includedLib1 ==> [TestCompositeLib1]:
            includedLib1.app ==> [TestCompositeLib1]:app
            includedLib1.app.androidTest ==> [TestCompositeLib1]:app/ANDROID_TEST
            includedLib1.app.main ==> [TestCompositeLib1]:app/MAIN
            includedLib1.app.unitTest ==> [TestCompositeLib1]:app/UNIT_TEST
            includedLib1.lib ==> [TestCompositeLib1]:lib
            includedLib1.lib.androidTest ==> [TestCompositeLib1]:lib/ANDROID_TEST
            includedLib1.lib.main ==> [TestCompositeLib1]:lib/MAIN
            includedLib1.lib.unitTest ==> [TestCompositeLib1]:lib/UNIT_TEST
            includedLib1.TestCompositeLibNested_1 ==> [TestCompositeLib1/TestCompositeLibNested_1]:
            includedLib1.TestCompositeLibNested_1.main ==> [TestCompositeLib1/TestCompositeLibNested_1]:/MAIN
            includedLib1.TestCompositeLibNested_1.test ==> [TestCompositeLib1/TestCompositeLibNested_1]:/test
            TestCompositeLib2 ==> [TestCompositeLib2]:
            TestCompositeLib2.main ==> [TestCompositeLib2]:/MAIN
            TestCompositeLib2.test ==> [TestCompositeLib2]:/test
            TestCompositeLib3 ==> [TestCompositeLib3]:
            TestCompositeLib3.app ==> [TestCompositeLib3]:app
            TestCompositeLib3.app.androidTest ==> [TestCompositeLib3]:app/ANDROID_TEST
            TestCompositeLib3.app.main ==> [TestCompositeLib3]:app/MAIN
            TestCompositeLib3.app.unitTest ==> [TestCompositeLib3]:app/UNIT_TEST
            TestCompositeLib3.lib ==> [TestCompositeLib3]:lib
            TestCompositeLib3.lib.androidTest ==> [TestCompositeLib3]:lib/ANDROID_TEST
            TestCompositeLib3.lib.main ==> [TestCompositeLib3]:lib/MAIN
            TestCompositeLib3.lib.unitTest ==> [TestCompositeLib3]:lib/UNIT_TEST
            TestCompositeLib3.TestCompositeLibNested_3 ==> [TestCompositeLib3/TestCompositeLibNested_3]:
            TestCompositeLib3.TestCompositeLibNested_3.main ==> [TestCompositeLib3/TestCompositeLibNested_3]:/MAIN
            TestCompositeLib3.TestCompositeLibNested_3.test ==> [TestCompositeLib3/TestCompositeLibNested_3]:/test
            TestCompositeLib4 ==> [TestCompositeLib4]:
            TestCompositeLib4.main ==> [TestCompositeLib4]:/MAIN
            TestCompositeLib4.test ==> [TestCompositeLib4]:/test
        """.trimIndent()
        )
      } else {
        assertThat(dumpModuleToGradlePathMapping(project, preparedProject.root)).isEqualTo(
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
            compositeNest ==> [TestCompositeLib1/TestCompositeLibNested_1]:
            compositeNest.main ==> [TestCompositeLib1/TestCompositeLibNested_1]:/MAIN
            compositeNest.test ==> [TestCompositeLib1/TestCompositeLibNested_1]:/test
            composite2 ==> [TestCompositeLib2]:
            composite2.main ==> [TestCompositeLib2]:/MAIN
            composite2.test ==> [TestCompositeLib2]:/test
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
            com.test.compositeNest3.compositeNest.main ==> [TestCompositeLib3/TestCompositeLibNested_3]:/MAIN
            com.test.compositeNest3.compositeNest.test ==> [TestCompositeLib3/TestCompositeLibNested_3]:/test
            composite4 ==> [TestCompositeLib4]:
            composite4.main ==> [TestCompositeLib4]:/MAIN
            composite4.test ==> [TestCompositeLib4]:/test
        """.trimIndent()
        )

      }

      assertThatProjectPathsCanBeResolved(project)
    }
  }

  @Test
  fun rootBuildRelativeGradleProjectPaths_inComposites() {
    val preparedProject = projectRule.prepareTestProject(TestProject.COMPOSITE_BUILD)
    preparedProject.open { project ->
      if (phasedSync) {
        assertThat(dumpModuleToRootBuildRelativeGradlePathMapping(project)).isEqualTo(
          """
            ==> :
            .app ==> :app
            .app.androidTest ==> :app
            .app.main ==> :app
            .app.unitTest ==> :app
            .lib ==> :lib
            .lib.androidTest ==> :lib
            .lib.main ==> :lib
            .lib.unitTest ==> :lib
            includedLib1 ==> :includedLib1
            includedLib1.app ==> :includedLib1:app
            includedLib1.app.androidTest ==> :includedLib1:app
            includedLib1.app.main ==> :includedLib1:app
            includedLib1.app.unitTest ==> :includedLib1:app
            includedLib1.lib ==> :includedLib1:lib
            includedLib1.lib.androidTest ==> :includedLib1:lib
            includedLib1.lib.main ==> :includedLib1:lib
            includedLib1.lib.unitTest ==> :includedLib1:lib
            includedLib1.TestCompositeLibNested_1 ==> :includedLib1:TestCompositeLibNested_1
            includedLib1.TestCompositeLibNested_1.main ==> :includedLib1:TestCompositeLibNested_1
            includedLib1.TestCompositeLibNested_1.test ==> :includedLib1:TestCompositeLibNested_1
            TestCompositeLib2 ==> :TestCompositeLib2
            TestCompositeLib2.main ==> :TestCompositeLib2
            TestCompositeLib2.test ==> :TestCompositeLib2
            TestCompositeLib3 ==> :TestCompositeLib3
            TestCompositeLib3.app ==> :TestCompositeLib3:app
            TestCompositeLib3.app.androidTest ==> :TestCompositeLib3:app
            TestCompositeLib3.app.main ==> :TestCompositeLib3:app
            TestCompositeLib3.app.unitTest ==> :TestCompositeLib3:app
            TestCompositeLib3.lib ==> :TestCompositeLib3:lib
            TestCompositeLib3.lib.androidTest ==> :TestCompositeLib3:lib
            TestCompositeLib3.lib.main ==> :TestCompositeLib3:lib
            TestCompositeLib3.lib.unitTest ==> :TestCompositeLib3:lib
            TestCompositeLib3.TestCompositeLibNested_3 ==> :TestCompositeLib3:TestCompositeLibNested_3
            TestCompositeLib3.TestCompositeLibNested_3.main ==> :TestCompositeLib3:TestCompositeLibNested_3
            TestCompositeLib3.TestCompositeLibNested_3.test ==> :TestCompositeLib3:TestCompositeLibNested_3
            TestCompositeLib4 ==> :TestCompositeLib4
            TestCompositeLib4.main ==> :TestCompositeLib4
            TestCompositeLib4.test ==> :TestCompositeLib4
        """.trimIndent()
        )
      } else {
        assertThat(dumpModuleToRootBuildRelativeGradlePathMapping(project)).isEqualTo(
          """
            ==> :
            .app ==> :app
            .app.androidTest ==> :app
            .app.main ==> :app
            .app.unitTest ==> :app
            .lib ==> :lib
            .lib.androidTest ==> :lib
            .lib.main ==> :lib
            .lib.unitTest ==> :lib
            TestCompositeLib1 ==> :includedLib1
            TestCompositeLib1.app ==> :includedLib1:app
            TestCompositeLib1.app.androidTest ==> :includedLib1:app
            TestCompositeLib1.app.main ==> :includedLib1:app
            TestCompositeLib1.app.unitTest ==> :includedLib1:app
            TestCompositeLib1.lib ==> :includedLib1:lib
            TestCompositeLib1.lib.androidTest ==> :includedLib1:lib
            TestCompositeLib1.lib.main ==> :includedLib1:lib
            TestCompositeLib1.lib.unitTest ==> :includedLib1:lib
            compositeNest ==> :includedLib1:TestCompositeLibNested_1
            compositeNest.main ==> :includedLib1:TestCompositeLibNested_1
            compositeNest.test ==> :includedLib1:TestCompositeLibNested_1
            composite2 ==> :TestCompositeLib2
            composite2.main ==> :TestCompositeLib2
            composite2.test ==> :TestCompositeLib2
            TestCompositeLib3 ==> :TestCompositeLib3
            TestCompositeLib3.app ==> :TestCompositeLib3:app
            TestCompositeLib3.app.androidTest ==> :TestCompositeLib3:app
            TestCompositeLib3.app.main ==> :TestCompositeLib3:app
            TestCompositeLib3.app.unitTest ==> :TestCompositeLib3:app
            TestCompositeLib3.lib ==> :TestCompositeLib3:lib
            TestCompositeLib3.lib.androidTest ==> :TestCompositeLib3:lib
            TestCompositeLib3.lib.main ==> :TestCompositeLib3:lib
            TestCompositeLib3.lib.unitTest ==> :TestCompositeLib3:lib
            com.test.compositeNest3.compositeNest ==> :TestCompositeLib3:TestCompositeLibNested_3
            com.test.compositeNest3.compositeNest.main ==> :TestCompositeLib3:TestCompositeLibNested_3
            com.test.compositeNest3.compositeNest.test ==> :TestCompositeLib3:TestCompositeLibNested_3
            composite4 ==> :TestCompositeLib4
            composite4.main ==> :TestCompositeLib4
            composite4.test ==> :TestCompositeLib4
        """.trimIndent()
        )

      }
      assertThatProjectPathsCanBeResolved(project)
    }
  }

  @Test
  fun updatesResolutionCache() {
    val preparedProject = projectRule.prepareTestProject(TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES)
    preparedProject.open { project ->
      assertThatProjectPathsCanBeResolved(project)

      // Move :app to :app:main and thus cause some modules to be re-created or at least new modules to be created. If the project wide
      // cache is not reset the expectation below fails.
      moveAppToAppMain(preparedProject.root)
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

  private fun dumpModuleToGradlePathMapping(project: Project, root: File): String {
    return ModuleManager.getInstance(project).modules.map { it to it.getGradleProjectPath() }
      .sortedWith(compareBy({ it.second?.buildRoot }, { it.first.name }))
      .joinToString("\n") { (module, gradleProjectPath) ->
        val moduleName = module.name.removePrefix(project.name)
        "$moduleName ==>${
          gradleProjectPath?.let {
            val buildId = gradleProjectPath.buildRoot.let(::File).relativeToOrSelf(root).path.nullize()?.let { "[$it]" }.orEmpty()
            val gradlePath = gradleProjectPath.path
            val sourceSet = (gradleProjectPath as? GradleSourceSetProjectPath)?.sourceSet?.let { "/$it" }.orEmpty()
            " ${buildId}$gradlePath$sourceSet"
          } ?: ""
        }"
      }
      .trim()
  }

  private fun dumpModuleToRootBuildRelativeGradlePathMapping(project: Project): String {
    return ModuleManager.getInstance(project)
      .modules
      .sortedWith(compareBy({ it.getGradleProjectPath()?.buildRoot }, { it.name }))
      .mapNotNull { it to (it.getGradleIdentityPath() ?: return@mapNotNull null) }
      .joinToString("\n") { (module, gradleProjectPath) ->
        val moduleName = module.name.removePrefix(project.name)
        "$moduleName ==> $gradleProjectPath"
      }
      .trim()
  }

  private fun assertThatProjectPathsCanBeResolved(project: Project) {
    val pathMap = ModuleManager.getInstance(project).modules.map { it to it.getGradleProjectPath() }
    pathMap.forEach { (module, gradlePath) ->
      if (gradlePath != null) assertThat(gradlePath.resolveIn(project)).isSameAs(module)
    }
  }
}
