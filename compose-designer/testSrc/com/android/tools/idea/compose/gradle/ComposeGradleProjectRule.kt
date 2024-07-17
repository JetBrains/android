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
package com.android.tools.idea.compose.gradle

import com.android.flags.junit.FlagRule
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.compose.preview.TEST_DATA_PATH
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.createNoSecurityRenderService
import com.android.tools.idea.run.deployment.liveedit.registerComposeCompilerPlugin
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.NamedExternalResource
import com.android.tools.idea.testing.TestLoggerRule
import com.android.tools.idea.testing.buildAndWait
import com.android.tools.idea.testing.withKotlin
import com.android.tools.rendering.RenderService
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.io.File
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Default Kotlin version used for Compose projects using this rule. */
internal const val DEFAULT_KOTLIN_VERSION = "1.7.20"

/**
 * [TestRule] that implements the [before] and [after] setup specific for Compose rendering tests.
 */
private class ComposeGradleProjectRuleImpl(
  private val projectPath: String,
  private val testDataPath: String,
  private val kotlinVersion: String,
  private val projectRule: AndroidGradleProjectRule,
) : NamedExternalResource() {
  override fun before(description: Description) {
    registerComposeCompilerPlugin(projectRule.project)

    RenderService.shutdownRenderExecutor(5)
    RenderService.initializeRenderExecutor()
    StudioRenderService.setForTesting(projectRule.project, createNoSecurityRenderService())
    projectRule.fixture.testDataPath = resolveWorkspacePath(testDataPath).toString()
    projectRule.load(projectPath, AGP_CURRENT.withKotlin(kotlinVersion))

    projectRule.invokeTasks("compileDebugSources").apply {
      buildError?.printStackTrace()
      Assert.assertTrue(
        "The project must compile correctly for the test to pass",
        isBuildSuccessful,
      )
    }
  }

  override fun after(description: Description) {
    StudioRenderService.setForTesting(projectRule.project, null)
  }
}

/**
 * A [TestRule] providing the same behaviour as [AndroidGradleProjectRule] but with the correct
 * setup for rendeering Compose elements.
 */
open class ComposeGradleProjectRule(
  projectPath: String,
  testDataPath: String = TEST_DATA_PATH,
  kotlinVersion: String = DEFAULT_KOTLIN_VERSION,
  private val projectRule: AndroidGradleProjectRule = AndroidGradleProjectRule(),
) : TestRule {
  val project: Project
    get() = projectRule.project

  val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  protected open val delegate: RuleChain =
    RuleChain.outerRule(TestLoggerRule())
      .around(projectRule)
      .around(ComposeGradleProjectRuleImpl(projectPath, testDataPath, kotlinVersion, projectRule))
      .around(EdtRule())
      .around(FlagRule(StudioFlags.GRADLE_SAVE_LOG_TO_FILE, true))

  fun androidFacet(gradlePath: String) = projectRule.androidFacet(gradlePath)

  override fun apply(base: Statement, description: Description): Statement =
    delegate.apply(base, description)

  fun clean() = GradleBuildInvoker.getInstance(project).cleanProject()

  fun build(): GradleInvocationResult {
    val invocationResult =
      project.buildAndWait() { invoker ->
        invoker.executeTasks(
          GradleBuildInvoker.Request.builder(
              project,
              File(project.basePath!!),
              "compileDebugSources",
            )
            .setMode(BuildMode.COMPILE_JAVA)
            .build()
        )
      }
    invokeAndWaitIfNeeded { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }
    return invocationResult
  }

  fun buildAndAssertIsSuccessful() {
    build().also {
      it.buildError?.printStackTrace()
      assertTrue(it.isBuildSuccessful)
    }
  }

  fun requestSyncAndWait() {
    projectRule.requestSyncAndWait()
  }
}
