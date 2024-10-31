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
package com.android.tools.idea.gradle.project.upgrade.integration

import com.android.SdkConstants
import com.android.ide.common.repository.AgpVersion
import com.android.sdklib.AndroidVersion
import com.android.testutils.junit4.OldAgpSuite
import com.android.tools.idea.gradle.dsl.utils.FN_GRADLE_PROPERTIES
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncInvokerImpl
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeRefactoringProcessor
import com.android.tools.idea.gradle.util.CompatibleGradleVersion
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.sdk.Jdks
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironment
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.BuildEnvironment
import com.android.tools.idea.testing.CustomAgpVersionSoftwareEnvironment
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.JdkUtils
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.resolve
import com.android.tools.idea.testing.withGradle
import com.android.utils.FileUtils
import com.google.common.truth.Expect
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.EdtRule
import junit.framework.TestCase
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

open class ProjectsUpgradeTestBase {

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

  @get:Rule
  val projectRule = AndroidGradleProjectRule("tools/adt/idea/project-system-gradle-upgrade/testData/upgrade/Projects")

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @get:Rule
  val edtRule = EdtRule()

  private val fakeSyncInvoker = FakeInvoker()

  @Before
  fun setUp() {
    val ideComponents = IdeComponents(projectRule.fixture)
    // Allows to skip sync request after upgrade.
    ideComponents.replaceApplicationService(GradleSyncInvoker::class.java, fakeSyncInvoker)
  }

  fun doTestFullUpgrade(baseProject: AUATestProjectState, to: AUATestProjectState) {
    loadAUATestProject(baseProject)
    addStateEmbeddedJdkToTable(to)

    fakeSyncInvoker.fakeNextSyncSuccess = true
    //TODO run upgrade through FakeUI instead.
    val processor = AgpUpgradeRefactoringProcessor(projectRule.project, baseProject.agpVersion(), to.agpVersion())
    processor.componentRefactoringProcessors.forEach {
      it.isEnabled = when (it.necessity()) {
        AgpUpgradeComponentNecessity.IRRELEVANT_FUTURE -> false
        AgpUpgradeComponentNecessity.IRRELEVANT_PAST -> false
        else -> true
      }
    }

    processor.run()

    verifyFilesStateAsExpected(to)
    Truth.assertThat(fakeSyncInvoker.callsCount).isEqualTo(2)
  }

  fun doTestMinimalUpgrade(baseProject: AUATestProjectState, to: AUATestProjectState) {
    loadAUATestProject(baseProject)
    addStateEmbeddedJdkToTable(to)
    fakeSyncInvoker.fakeNextSyncSuccess = true
    //TODO run upgrade through FakeUI instead.
    val processor = AgpUpgradeRefactoringProcessor(projectRule.project, baseProject.agpVersion(), to.agpVersion())
    processor.componentRefactoringProcessors.forEach {
      it.isEnabled = when (it.necessity()) {
        AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT -> true
        AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT -> true
        else -> false
      }
    }

    processor.run()

    verifyFilesStateAsExpected(to)
    Truth.assertThat(fakeSyncInvoker.callsCount).isEqualTo(2)
  }

  fun loadAUATestProject(testProject: AUATestProjectState) = projectRule.load(
    projectPath = testProject.projectBasePath(),
    agpVersion = testProject.agpVersionDef(),
    ndkVersion = testProject.ndkVersion()
  ) { projectRoot ->
    applyProjectPatch(testProject, projectRoot)
  }

  private fun addStateEmbeddedJdkToTable(state: AUATestProjectState) {
    state.jdkVersion()?.let {
      val embeddedToJdk = JdkUtils.getEmbeddedJdkPathWithVersion(it)
      Jdks.getInstance().createAndAddJdk(embeddedToJdk.path)
    }
  }

  private fun applyProjectPatch(testProject: AUATestProjectState, projectRoot: File) {
    val projectPatchPath = testProject.projectPatchPath()
    if (projectPatchPath != null) {
      // Base project is copied and patched. Need to just overwrite the changed files and patch them.
      val srcRoot = projectRule.resolveTestDataPath(projectPatchPath)
      TestCase.assertTrue(srcRoot.getPath(), srcRoot.exists())
      FileUtils.getAllFiles(srcRoot).forEach { source ->
        val relative = source.relativeTo(srcRoot)
        val target = projectRoot.resolve(relative)
        FileUtils.copyFile(source, target)
        // Update dependencies to latest, and possibly repository URL too if android.mavenRepoUrl is set
        val environment = testProject.agpVersionDef().resolve()
        AndroidGradleTests.updateToolingVersionsAndPaths(
          target,
          environment,
          testProject.ndkVersion(),
          emptyList()
        )
        when (relative.path) {
          FN_GRADLE_PROPERTIES -> {
            VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
            AndroidGradleTests.updateGradleProperties(projectRoot, AgpVersion.parse(environment.agpVersion), AndroidVersion(environment.compileSdk));
          }
        }
      }
    }
  }

  private fun prepareProjectForCheck(expectedProjectState: AUATestProjectState): File {
    prepareGradleProject(
      projectRule.resolveTestDataPath(expectedProjectState.projectBasePath()),
      temporaryFolder.root
    ) { projectRoot: File ->
      // Load project with base gradle version first. We can not pass expected updated version here because
      // it will try to verify the binary actually exists. That would require to get all versions of gradle
      // for every test target. But we don't care about binary existing in this case, we are not going to run
      // this project, we just need its source files for comparison.
      // So set existing gradle version here and change it to expected one after project is prepared.
      val baseGradleVersion = OldAgpSuite.GRADLE_VERSION?.takeIf { it != "LATEST" }
      val resolvedAgpVersion = expectedProjectState.agpVersionDef().withGradle(baseGradleVersion).resolve()
      AndroidGradleTests.defaultPatchPreparedProject(
        projectRoot,
        resolvedAgpVersion,
        expectedProjectState.ndkVersion()
      )
      JdkUtils.overrideProjectGradleJdkPathWithVersion(projectRoot, resolvedAgpVersion.jdkVersion)
      // Patch base project with files expected to change.
      // Note: one could think that we only need to check these files instead of comparing all files recursively
      // but checking all project files allows us to make sure no unexpected changes were made to any other not listed files.
      applyProjectPatch(expectedProjectState, projectRoot)
      // Setting actual expected gradle path here, which does not use EmbeddedDistributionPaths.findEmbeddedGradleDistributionFile() or
      // AndroidGradleTests.createGradleWrapper() because the file does not necessarily exist (because of bazel sandboxing, for example).
      val wrapper = GradleWrapper.create(projectRoot, null)
      GradleProjectSystemUtil.findEmbeddedGradleDistributionPath()
        ?.resolve("gradle-${expectedProjectState.gradleVersionString()}-bin.zip")
        ?.let { file -> wrapper.updateDistributionUrl(file) } ?: error("failed to set expected Gradle path")
    }
    return temporaryFolder.root
  }

  private fun verifyFilesStateAsExpected(expectedProjectState: AUATestProjectState) {
    val goldenProjectRoot = prepareProjectForCheck(expectedProjectState)
    val projectRoot = File(projectRule.project.basePath!!)

    FileUtils.getAllFiles(goldenProjectRoot)
      .filter { it != null }
      .mapNotNull { it.getExpectFun(expectedProjectState)?.let { f -> it to f } }
      .forEach {
        val file = it.first
        val expectedContent = file.readText()
        val relativePath = FileUtil.getRelativePath(goldenProjectRoot, file)
        val actualContent = FileUtils.join(projectRoot, relativePath).readText()
        it.second.invoke(relativePath, actualContent, expectedContent)
      }
  }

  private fun File.getExpectFun(expectedProjectState: AUATestProjectState): ((String?, String, String) -> Unit)? =
    takeIf { it.isFile }?.let {
      when {
        path.endsWith(SdkConstants.DOT_GRADLE) ||
        path.endsWith(SdkConstants.EXT_GRADLE_KTS) ||
        path.endsWith(SdkConstants.DOT_XML) ||
        path.endsWith(SdkConstants.DOT_JAVA) ||
        path.endsWith(SdkConstants.DOT_KT) ->
          { relativePath: String?, actualContent: String, goldenContent: String ->
            expect.withMessage(relativePath).that(actualContent).isEqualTo(goldenContent)
          }

        path.endsWith(SdkConstants.DOT_PROPERTIES) ->
        { relativePath: String?, actualContent: String, goldenContent: String ->
          val expectedContainsSuppression = goldenContent.contains("android.suppressUnsupportedCompileSdk=")
          expect.withMessage(relativePath).that(actualContent.lines().filter { !it.startsWith("#") && (expectedContainsSuppression || !it.startsWith("android.suppressUnsupportedCompileSdk=")) }.joinToString("\n"))
            .isEqualTo(goldenContent.lines().filter { !it.startsWith("#") }.joinToString("\n")) }

        else -> null
      }
    }

  private class FakeInvoker(val delegate: GradleSyncInvoker = GradleSyncInvokerImpl()) : GradleSyncInvoker by delegate {
    var callsCount = 0
    var fakeNextSyncSuccess = false

    override fun requestProjectSync(project: Project, request: GradleSyncInvoker.Request, listener: GradleSyncListener?) {
      callsCount++
      if (fakeNextSyncSuccess) listener?.syncSucceeded(project)
      else delegate.requestProjectSync(project, request, listener)
    }
  }

  private fun AUATestProjectState.agpVersionString() = version.agpVersion ?: BuildEnvironment.getInstance().gradlePluginVersion
  private fun AUATestProjectState.agpVersion() = AgpVersion.parse(agpVersionString())
  private fun AUATestProjectState.gradleVersion() = CompatibleGradleVersion.getCompatibleGradleVersion(agpVersion()).version
  private fun AUATestProjectState.gradleVersionString() = gradleVersion().version

  private fun AUATestProjectState.jdkVersion() = version.jdkVersion
  private fun AUATestProjectState.kotlinVersion() = version.kotlinVersion

  private fun AUATestProjectState.agpVersionDef(): AgpVersionSoftwareEnvironment =
    CustomAgpVersionSoftwareEnvironment(
      agpVersion = agpVersionString(),
      gradleVersion = gradleVersionString(),
      jdkVersion = jdkVersion(),
      kotlinVersion = kotlinVersion()
    )

  private fun AUATestProjectState.ndkVersion(): String? = null
}