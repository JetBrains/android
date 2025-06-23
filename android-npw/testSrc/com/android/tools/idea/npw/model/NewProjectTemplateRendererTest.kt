/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw.model

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.extensions.getPropertyPath
import com.android.tools.idea.gradle.plugin.AgpVersions
import com.android.tools.idea.gradle.project.importing.GradleJdkConfigurationInitializer
import com.android.tools.idea.gradle.toolchain.GradleDaemonJvmCriteriaTemplatesManager
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.npw.project.DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.gradle.jdk.GradleDefaultJvmCriteriaStore
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidGradleTests.getLocalRepositoriesForGroovy
import com.android.tools.idea.testing.AndroidGradleTests.getLocalRepositoriesForKotlin
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.FOOJAY_RESOLVER_CONVENTION_NAME
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptSettingsPsiFile
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.util.toJvmVendor
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewProjectTemplateRendererTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private val projectBasePath by lazy { projectRule.project.basePath!! }
  private val multiTemplateRenderer: MultiTemplateRenderer
    get() = MultiTemplateRenderer { renderer ->
      object : Task.Modal(projectRule.project, "Test", false) {
        override fun run(indicator: ProgressIndicator) {
          renderer(project)
        }
      }.queue()
    }

  @Before
  fun setUp() {
    Registry.get("gradle.daemon.jvm.criteria.new.project").setValue(true)
    GradleJdkConfigurationInitializer.getInstance().canInitializeDaemonJvmCriteria = true
    val embeddedGradleDistribution = GradleProjectSystemUtil.findEmbeddedGradleDistributionPath()?.toURI()?.toURL()?.toString()
    StudioFlags.GRADLE_LOCAL_DISTRIBUTION_URL.override(embeddedGradleDistribution)
    StudioFlags.NPW_DAEMON_JVM_CRITERIA_REQUIRED_GRADLE_VERSION.override("8.10")
    IdeSdks.removeJdksOn(projectRule.testRootDisposable)
  }

  @After
  fun tearDown() {
    Registry.get("gradle.daemon.jvm.criteria.new.project").resetToDefault()
    GradleJdkConfigurationInitializer.getInstance().canInitializeDaemonJvmCriteria = false
    StudioFlags.GRADLE_LOCAL_DISTRIBUTION_URL.clearOverride()
    StudioFlags.NPW_DAEMON_JVM_CRITERIA_REQUIRED_GRADLE_VERSION.clearOverride()
  }

  @Test
  fun `Given gradle version without toolchain as default When create project Then Foojay plugin and Daemon JVM criteria are not defined`() {
    val render = createNewProjectTemplateRender("8.0")
    multiTemplateRenderer.requestRender(render)

    assertFoojayPlugin(false)
    assertGradleDaemonJvmCriteriaNotDefined()
  }

  @Test
  fun `Given gradle version with toolchain as default When create project using KTS Then Foojay plugin and Daemon JVM criteria are defined`() {
    val render = createNewProjectTemplateRender("9.1", true)
    multiTemplateRenderer.requestRender(render)

    assertFoojayPlugin(true)
    assertGradleDaemonJvmCriteria(21)
  }

  @Test
  fun `Given gradle version with toolchain as default When create project not using KTS Then Foojay plugin and Daemon JVM criteria are defined`() {
    val render = createNewProjectTemplateRender("9.2", true)
    multiTemplateRenderer.requestRender(render)

    assertFoojayPlugin(true)
    assertGradleDaemonJvmCriteria(21)
  }

  @Test
  fun `Given gradle version with toolchain as default but disable registry When create project Then Foojay plugin and Daemon JVM criteria are not defined`() {
    Registry.get("gradle.daemon.jvm.criteria.new.project").setValue(false)

    val render = createNewProjectTemplateRender("9.1")
    multiTemplateRenderer.requestRender(render)

    assertFoojayPlugin(false)
    assertGradleDaemonJvmCriteriaNotDefined()
  }

  @Test
  fun `Given gradle version with toolchain as default and defined default criteria When create project Then Foojay plugin and Daemon JVM criteria are defined`() {
    GradleDefaultJvmCriteriaStore.daemonJvmCriteria = GradleDaemonJvmCriteria("17", "tencent".toJvmVendor())

    // Using version of Gradle that doesn't generate the download URLs when executing updateDaemonJvm task that's
    // because 'foojay-resolver' plugin requires to access 'api.foojay.io' host which will fail when running from bazel
    val render = createNewProjectTemplateRender("8.11.1", false)
    multiTemplateRenderer.requestRender(render)

    assertBasicGradleDaemonJvmCriteria(17, "tencent")
  }

  private fun createNewProjectTemplateRender(
    gradleVersion: String, useGradleKts: Boolean = false
  ) : NewProjectModel.ProjectTemplateRenderer {
    val newProjectModel = spy(NewProjectModel())
    val render = spy(newProjectModel.ProjectTemplateRenderer())
    val projectTemplateDataBuilder = spy(newProjectModel.projectTemplateDataBuilder)
    val projectTemplateData = createSimpleProjectTemplateData(gradleVersion)

    newProjectModel.project = projectRule.project
    doReturn(projectTemplateData).whenever(projectTemplateDataBuilder).build()
    doReturn(StringValueProperty(projectBasePath)).whenever(newProjectModel).projectLocation
    doReturn(projectTemplateDataBuilder).whenever(newProjectModel).projectTemplateDataBuilder
    doReturn(BoolValueProperty(useGradleKts)).whenever(newProjectModel).useGradleKts
    doAnswer {
      addLocalRepositoriesToResolveFoojayPlugin(useGradleKts)
      it.callRealMethod()
    }.whenever(render).onSourcesCreated()
    return render
  }

  private fun createSimpleProjectTemplateData(gradleVersion: String) = ProjectTemplateData(
    false,
    AgpVersions.newProject,
    GradleVersion.version(gradleVersion),
    listOf(),
    null,
    Language.Java,
    DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS,
    projectRule.project.guessProjectDir()!!.toIoFile(),
    "com.test.packagename",
    mapOf(),
    null,
    null,
    true,
  )

  private fun assertFoojayPlugin(isApplied: Boolean) {
    assertEquals(isApplied, ProjectBuildModel.get(projectRule.project).projectSettingsModel!!.plugins().declaredProperties.any {
      it.valueAsString()!!.contains(FOOJAY_RESOLVER_CONVENTION_NAME)
    })
  }

  private fun assertGradleDaemonJvmCriteriaNotDefined() {
    assertFalse(GradleDaemonJvmPropertiesFile.getPropertyPath(projectBasePath).exists())
  }

  private fun assertGradleDaemonJvmCriteria(expectedVersion: Int) {
    val daemonJvmCriteriaFile = GradleDaemonJvmPropertiesFile.getPropertyPath(projectBasePath)
    val currentDaemonJvmCriteriaFileContent = daemonJvmCriteriaFile.readText()
    assertTrue(daemonJvmCriteriaFile.deleteIfExists())

    GradleDaemonJvmCriteriaTemplatesManager.generatePropertiesFile(JavaVersion.compose(expectedVersion), projectBasePath)
    val expectedDaemonJvmCriteriaFileContent = daemonJvmCriteriaFile.readText()
    assertEquals(expectedDaemonJvmCriteriaFileContent, currentDaemonJvmCriteriaFileContent)
  }

  private fun assertBasicGradleDaemonJvmCriteria(expectedVersion: Int, expectedVendor: String?) {
    val daemonJvmCriteriaFile = GradleDaemonJvmPropertiesFile.getProperties(Path(projectBasePath))

    assertEquals(expectedVersion.toString(), daemonJvmCriteriaFile?.version?.value)
    assertEquals(expectedVendor, daemonJvmCriteriaFile?.vendor?.value)
  }

  private fun addLocalRepositoriesToResolveFoojayPlugin(useGradleKts: Boolean) {
    val gradleSettings = getTopLevelBuildScriptSettingsPsiFile(projectRule.project, projectBasePath)?.virtualFile?.toIoFile()
    val localRepositories = if (useGradleKts) {
      getLocalRepositoriesForKotlin(listOf<File>())
    } else {
      getLocalRepositoriesForGroovy(listOf<File>())
    }

    val gradleSettingsContent = gradleSettings!!.readText()
    val newGradleSettingsContent = AndroidGradleTests.updateLocalRepositories(gradleSettingsContent, localRepositories)
    Files.writeString(gradleSettings.toPath(), newGradleSettingsContent)
  }
}