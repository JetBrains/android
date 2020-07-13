package com.android.tools.idea.compose

import com.android.testutils.TestUtils
import com.android.tools.idea.compose.preview.TEST_DATA_PATH
import com.android.tools.idea.rendering.NoSecurityManagerRenderService
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.NamedExternalResource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Assert
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * [TestRule] that implements the [before] and [after] setup specific for Compose rendering tests.
 */
private class ComposeGradleProjectRuleImpl(private val projectPath: String,
                                           private val projectRule: AndroidGradleProjectRule) : NamedExternalResource() {
  override fun before(description: Description) {
    RenderService.shutdownRenderExecutor(5)
    RenderService.initializeRenderExecutor()
    RenderService.setForTesting(projectRule.project, NoSecurityManagerRenderService(projectRule.project))
    projectRule.fixture.testDataPath = TestUtils.getWorkspaceFile(TEST_DATA_PATH).path
    projectRule.load(projectPath)
    projectRule.requestSyncAndWait()

    ApplicationManager.getApplication().invokeAndWait {
      projectRule.invokeTasks("compileDebugSources").apply {
        buildError?.printStackTrace()
        Assert.assertTrue("The project must compile correctly for the test to pass", isBuildSuccessful)
      }
    }
  }

  override fun after(description: Description) {
    RenderService.setForTesting(projectRule.project, null)
  }
}

/**
 * A [TestRule] providing the same behaviour as [AndroidGradleProjectRule] but with the correct setup for rendeering
 * Compose elements.
 */
class ComposeGradleProjectRule(projectPath: String,
                               private val projectRule: AndroidGradleProjectRule = AndroidGradleProjectRule()) : TestRule {
  val project: Project
    get() = projectRule.project

  val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val delegate = RuleChain.outerRule(projectRule).around(ComposeGradleProjectRuleImpl(projectPath, projectRule))

  fun androidFacet(gradlePath: String) = projectRule.androidFacet(gradlePath)

  override fun apply(base: Statement, description: Description): Statement = delegate.apply(base, description)
}