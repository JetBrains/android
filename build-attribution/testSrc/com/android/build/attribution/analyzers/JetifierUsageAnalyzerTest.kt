/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.build.attribution.analyzers

import com.android.SdkConstants
import com.android.build.attribution.BuildAttributionManagerImpl
import com.android.build.attribution.ui.controllers.createCheckJetifierTaskRequest
import com.android.builder.model.PROPERTY_CHECK_JETIFIER_RESULT_FILE
import com.android.ide.common.attribution.CheckJetifierResult
import com.android.ide.common.attribution.DependencyPath
import com.android.ide.common.attribution.FullDependencyPath
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.Request.Companion.builder
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.TestProjectPaths.BUILD_ANALYZER_CHECK_JETIFIER
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil.toSystemDependentName
import org.jetbrains.android.AndroidTestBase
import org.junit.Test
import java.io.File

class JetifierUsageAnalyzerTest : AndroidGradleTestCase() {


  override fun getAdditionalRepos(): Collection<File> {
    return listOf(File(AndroidTestBase.getTestDataPath(), toSystemDependentName("$BUILD_ANALYZER_CHECK_JETIFIER/mavenRepo")))
  }

  override fun setUp() {
    super.setUp()
    StudioFlags.BUILD_ANALYZER_JETIFIER_ENABLED.override(true)
  }

  override fun tearDown() {
    super.tearDown()
    StudioFlags.BUILD_ANALYZER_JETIFIER_ENABLED.clearOverride()
  }

  @Test
  fun testNoAndroidX() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val result = invokeGradleTasks(project, "assembleDebug")

    Truth.assertThat(result.isBuildSuccessful).isTrue()

    val buildAttributionManager = project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl
    val jetifierUsageResult = buildAttributionManager.analyzersProxy.getJetifierUsageResult()
    Truth.assertThat(jetifierUsageResult).isEqualTo(JetifierNotUsed)
  }

  @Test
  fun testResultWhenFlagIsOff() {
    StudioFlags.BUILD_ANALYZER_JETIFIER_ENABLED.override(false)
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val result = invokeGradleTasks(project, "assembleDebug")

    Truth.assertThat(result.isBuildSuccessful).isTrue()

    val buildAttributionManager = project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl
    val jetifierUsageResult = buildAttributionManager.analyzersProxy.getJetifierUsageResult()
    Truth.assertThat(jetifierUsageResult).isEqualTo(AnalyzerNotRun)
  }

  private fun doTestInitialBuildResult(propertiesContent: String, expectedResult: JetifierUsageAnalyzerResult) {
    val rootFile = prepareProjectForImport(BUILD_ANALYZER_CHECK_JETIFIER)

    FileUtil.writeToFile(FileUtils.join(rootFile, SdkConstants.FN_GRADLE_PROPERTIES), propertiesContent)
    importProject()
    prepareProjectForTest(project, null)


    val result = invokeGradleTasks(project, "assembleDebug")

    Truth.assertThat(result.isBuildSuccessful).isTrue()

    val buildAttributionManager = project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl
    val jetifierUsageResult = buildAttributionManager.analyzersProxy.getJetifierUsageResult()
    Truth.assertThat(jetifierUsageResult).isEqualTo(expectedResult)
  }

  @Test
  fun testAndroidXAndJetifier() {
    doTestInitialBuildResult(
      propertiesContent = """
        android.useAndroidX=true
        android.enableJetifier=true
      """.trimIndent(),
      expectedResult = JetifierUsedCheckRequired
    )
  }

  @Test
  fun testAndroidXWithoutJetifier() {
    doTestInitialBuildResult(
      propertiesContent = """
        android.useAndroidX=true
      """.trimIndent(),
      expectedResult = JetifierNotUsed
    )
  }

  private fun doTestRunCheckJetifierTask(
    appBuildAdditionalDependencies: String,
    libBuildAdditionalDependencies: String,
    expectedJetifierUsageAnalyzerResult: JetifierUsageAnalyzerResult
  ) {
    prepareProjectForImport(BUILD_ANALYZER_CHECK_JETIFIER)

    FileUtils.join(projectFolderPath, "app", SdkConstants.FN_BUILD_GRADLE).let { file ->
      val newContent = file.readText()
        .replace(oldValue = "// This will be replaced by JetifierUsageAnalyzerTest", newValue = appBuildAdditionalDependencies)
      FileUtil.writeToFile(file, newContent)
    }
    FileUtils.join(projectFolderPath, "lib", SdkConstants.FN_BUILD_GRADLE).let { file ->
      val newContent = file.readText()
        .replace(oldValue = "// This will be replaced by JetifierUsageAnalyzerTest", newValue = libBuildAdditionalDependencies)
      FileUtil.writeToFile(file, newContent)
    }
    importProject()
    prepareProjectForTest(project, null)

    val originalBuildRequest = builder(project, projectFolderPath, "assembleDebug").build()
    val checkJetifierRequest = createCheckJetifierTaskRequest(originalBuildRequest)
    val checkJetifierResultProperty = checkJetifierRequest.commandLineArguments.first {
      it.contains(PROPERTY_CHECK_JETIFIER_RESULT_FILE)
    }
    val expectedResultFile = checkJetifierResultFile(checkJetifierRequest)
    Truth.assertThat(checkJetifierResultProperty.substringAfter("=")).isEqualTo(expectedResultFile.absolutePath)

    val result = invokeGradle(project) { gradleInvoker: GradleBuildInvoker ->
      gradleInvoker.executeTasks(checkJetifierRequest)
    }
    Truth.assertThat(result.isBuildSuccessful).isTrue()

    val buildAttributionManager = project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl
    val jetifierUsageResult = buildAttributionManager.analyzersProxy.getJetifierUsageResult()
    Truth.assertThat(jetifierUsageResult).isEqualTo(expectedJetifierUsageAnalyzerResult)
    Truth.assertThat(expectedResultFile.exists()).isFalse()

    // Verify running normal build after preserves the result.
    val result2 = invokeGradleTasks(project, "assembleDebug")
    Truth.assertThat(result2.isBuildSuccessful).isTrue()
    val jetifierUsageResult2 = buildAttributionManager.analyzersProxy.getJetifierUsageResult()
    Truth.assertThat(jetifierUsageResult2).isEqualTo(expectedJetifierUsageAnalyzerResult)
  }

  @Test
  fun testRunningCheckJetifierTaskWithRequiredLibs() {
    doTestRunCheckJetifierTask(
      appBuildAdditionalDependencies = """
      implementation 'example:A:1.0' // `A` transitively depends on a support library
      implementation 'com.android.support:collections:28.0.0'
    """.trimIndent(),
      libBuildAdditionalDependencies = """
      implementation 'example:B:1.0' // `B` directly depends on a support library
      implementation 'com.android.support:collections:28.0.0'
    """.trimIndent(),
      expectedJetifierUsageAnalyzerResult = JetifierRequiredForLibraries(
        CheckJetifierResult(LinkedHashMap<String, FullDependencyPath>().apply {
          put("example:A:1.0", FullDependencyPath(
            projectPath = ":app",
            configuration = "debugAndroidTestCompileClasspath",
            dependencyPath = DependencyPath(listOf("example:A:1.0", "example:B:1.0", "com.android.support:support-annotations:28.0.0"))
          ))
          put("com.android.support:collections:28.0.0", FullDependencyPath(
            projectPath = ":app",
            configuration = "debugAndroidTestCompileClasspath",
            dependencyPath = DependencyPath(listOf("com.android.support:collections:28.0.0"))
          ))
          put("example:B:1.0", FullDependencyPath(
            projectPath = ":lib",
            configuration = "debugAndroidTestCompileClasspath",
            dependencyPath = DependencyPath(listOf("example:B:1.0", "com.android.support:support-annotations:28.0.0"))
          ))
        }))
    )
  }

  @Test
  fun testRunningCheckJetifierTaskWithNoRequiredLibs() {
    doTestRunCheckJetifierTask(
      appBuildAdditionalDependencies = "",
      libBuildAdditionalDependencies = "",
      expectedJetifierUsageAnalyzerResult = JetifierCanBeRemoved
    )
  }

  //TODO (b/194299215): Test result is preserved in data folder and loaded from data folder after restart

}