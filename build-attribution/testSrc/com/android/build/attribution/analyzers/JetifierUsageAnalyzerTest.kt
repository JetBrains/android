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
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.data.BuildInvocationType
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.build.attribution.ui.controllers.createCheckJetifierTaskRequest
import com.android.builder.model.PROPERTY_CHECK_JETIFIER_RESULT_FILE
import com.android.ide.common.attribution.CheckJetifierResult
import com.android.ide.common.attribution.DependencyPath
import com.android.ide.common.attribution.FullDependencyPath
import com.android.ide.common.repository.GradleVersion
import com.android.testutils.MockitoKt
import com.android.tools.idea.Projects
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.Request.Companion.builder
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.TestProjectPaths.BUILD_ANALYZER_CHECK_JETIFIER
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil.toSystemDependentName
import org.jetbrains.android.AndroidTestBase
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
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

    val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getLatestBuildAnalysisResults()
    val jetifierUsageResult = results.getJetifierUsageResult()
    Truth.assertThat(jetifierUsageResult).isEqualTo(JetifierUsageAnalyzerResult(JetifierNotUsed))
  }

  @Test
  fun testResultWhenFlagIsOff() {
    StudioFlags.BUILD_ANALYZER_JETIFIER_ENABLED.override(false)
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val result = invokeGradleTasks(project, "assembleDebug")

    Truth.assertThat(result.isBuildSuccessful).isTrue()

    val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getLatestBuildAnalysisResults()
    val jetifierUsageResult = results.getJetifierUsageResult()
    Truth.assertThat(jetifierUsageResult).isEqualTo(JetifierUsageAnalyzerResult(AnalyzerNotRun))
  }

  private fun doTestInitialBuildResult(propertiesContent: String, expectedResult: JetifierUsageAnalyzerResult) {
    val rootFile = prepareProjectForImport(BUILD_ANALYZER_CHECK_JETIFIER)

    FileUtil.writeToFile(FileUtils.join(rootFile, SdkConstants.FN_GRADLE_PROPERTIES), propertiesContent)
    importProject()
    prepareProjectForTest(project, null)


    val result = invokeGradleTasks(project, "assembleDebug")

    Truth.assertThat(result.isBuildSuccessful).isTrue()

    val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getLatestBuildAnalysisResults()
    val jetifierUsageResult = results.getJetifierUsageResult()
    Truth.assertThat(jetifierUsageResult).isEqualTo(expectedResult)
  }

  @Test
  fun testAndroidXAndJetifier() {
    doTestInitialBuildResult(
      propertiesContent = """
        android.useAndroidX=true
        android.enableJetifier=true
      """.trimIndent(),
      expectedResult = JetifierUsageAnalyzerResult(JetifierUsedCheckRequired, lastCheckJetifierBuildTimestamp = null, checkJetifierBuild = false)
    )
  }

  @Test
  fun testAndroidXWithoutJetifier() {
    doTestInitialBuildResult(
      propertiesContent = """
        android.useAndroidX=true
      """.trimIndent(),
      expectedResult = JetifierUsageAnalyzerResult(JetifierNotUsed, lastCheckJetifierBuildTimestamp = null, checkJetifierBuild = false)
    )
  }

  private fun doTestRunCheckJetifierTask(
    appBuildAdditionalDependencies: String,
    libBuildAdditionalDependencies: String,
    expectedProjectStatus: JetifierUsageProjectStatus
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

    val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getLatestBuildAnalysisResults()
    val jetifierUsageResult = results.getJetifierUsageResult()
    Truth.assertThat(jetifierUsageResult.projectStatus).isEqualTo(expectedProjectStatus)
    Truth.assertThat(jetifierUsageResult.checkJetifierBuild).isEqualTo(true)
    Truth.assertThat(jetifierUsageResult.lastCheckJetifierBuildTimestamp).isNotNull()
    Truth.assertThat(expectedResultFile.exists()).isFalse()

    // Verify running normal build after preserves the result.
    val result2 = invokeGradleTasks(project, "assembleDebug")
    Truth.assertThat(result2.isBuildSuccessful).isTrue()

    val results2 = buildAnalyzerStorageManager.getLatestBuildAnalysisResults()
    val jetifierUsageResult2 = results2.getJetifierUsageResult()
    Truth.assertThat(jetifierUsageResult2.projectStatus).isEqualTo(expectedProjectStatus)
    Truth.assertThat(jetifierUsageResult2.checkJetifierBuild).isEqualTo(false)
    Truth.assertThat(jetifierUsageResult2.lastCheckJetifierBuildTimestamp).isEqualTo(jetifierUsageResult.lastCheckJetifierBuildTimestamp)
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
      expectedProjectStatus = JetifierRequiredForLibraries(
        CheckJetifierResult(sortedMapOf(
          "example:A:1.0" to listOf(FullDependencyPath(
            projectPath = ":app",
            configuration = "debugAndroidTestCompileClasspath",
            dependencyPath = DependencyPath(listOf("example:A:1.0", "example:B:1.0", "com.android.support:support-annotations:28.0.0"))
          )),
          "com.android.support:collections:28.0.0" to listOf(
            FullDependencyPath(
              projectPath = ":app",
              configuration = "debugAndroidTestCompileClasspath",
              dependencyPath = DependencyPath(listOf("com.android.support:collections:28.0.0"))
            ),
            FullDependencyPath(
              projectPath = ":lib",
              configuration = "debugAndroidTestCompileClasspath",
              dependencyPath = DependencyPath(listOf("com.android.support:collections:28.0.0"))
            )
          ),
          "example:B:1.0" to listOf(FullDependencyPath(
            projectPath = ":lib",
            configuration = "debugAndroidTestCompileClasspath",
            dependencyPath = DependencyPath(listOf("example:B:1.0", "com.android.support:support-annotations:28.0.0"))
          ))
        ))
      )
    )
  }

  @Test
  fun testRunningCheckJetifierTaskWithNoRequiredLibs() {
    doTestRunCheckJetifierTask(
      appBuildAdditionalDependencies = "",
      libBuildAdditionalDependencies = "",
      expectedProjectStatus = JetifierCanBeRemoved
    )
  }
}

class JetifierUsageAnalyzerUnitTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  @Test
  fun testResultForAGPPre_7_1_beta() {
    //This is a more of a unit test for the sake of efficiency.
    StudioFlags.BUILD_ANALYZER_JETIFIER_ENABLED.override(true)
    val studioProvidedInfo = StudioProvidedInfo(
      agpVersion = GradleVersion.parse("7.1.0-alpha11"),
      gradleVersion = null,
      configurationCachingGradlePropertyState = null,
      buildInvocationType = BuildInvocationType.REGULAR_BUILD,
      enableJetifierPropertyState = true,
      useAndroidXPropertyState = true,
      buildRequestHolder = MockitoKt.mock()
    )
    val analysisResult = Mockito.mock(BuildEventsAnalyzersProxy::class.java)

    val analyzer = JetifierUsageAnalyzer()
    analyzer.runPostBuildAnalysis(analysisResult, studioProvidedInfo)

    Truth.assertThat(analyzer.result).isEqualTo(JetifierUsageAnalyzerResult(AnalyzerNotRun))
  }

  @Test
  fun testResultForAGP_7_1() {
    //This is a more of a unit test for the sake of efficiency.
    StudioFlags.BUILD_ANALYZER_JETIFIER_ENABLED.override(true)
    val studioProvidedInfo = StudioProvidedInfo(
      agpVersion = GradleVersion.parse("7.1.0"),
      gradleVersion = null,
      configurationCachingGradlePropertyState = null,
      buildInvocationType = BuildInvocationType.REGULAR_BUILD,
      enableJetifierPropertyState = true,
      useAndroidXPropertyState = true,
      buildRequestHolder = BuildRequestHolder(builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build())
    )
    val analysisResult = Mockito.mock(BuildEventsAnalyzersProxy::class.java)

    val analyzer = JetifierUsageAnalyzer()
    analyzer.runPostBuildAnalysis(analysisResult, studioProvidedInfo)

    Truth.assertThat(analyzer.result).isEqualTo(JetifierUsageAnalyzerResult(JetifierUsedCheckRequired))
  }
}