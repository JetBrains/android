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
package com.android.tools.idea.gradle.project.build.invoker

import com.android.testutils.AssumeUtil
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.build.events.GradleErrorQuickFixProvider
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.project.hyperlink.SyncMessageHyperlink
import com.android.tools.idea.project.messages.SyncMessage
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.google.wireless.android.sdk.stats.BuildErrorMessage.ErrorType.AAPT
import com.google.wireless.android.sdk.stats.BuildErrorMessage.ErrorType.UNKNOWN_ERROR_TYPE
import com.google.wireless.android.sdk.stats.BuildErrorMessage.ErrorType.XML_PARSER
import com.google.wireless.android.sdk.stats.BuildErrorMessage.FileType.PROJECT_FILE
import com.google.wireless.android.sdk.stats.BuildErrorMessage.FileType.UNKNOWN_FILE_TYPE
import com.intellij.build.BuildTreeConsoleView
import com.intellij.build.BuildViewManager
import com.intellij.build.ExecutionNode
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.FinishEventImpl
import com.intellij.build.events.impl.StartEventImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ui.tree.TreeUtil
import junit.framework.TestCase.assertEquals
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File
import java.util.function.Predicate
import javax.swing.tree.DefaultMutableTreeNode

@RunWith(Parameterized::class)
class BuildOutputParsersIntegrationTest {

  companion object {
    @JvmStatic
    @Parameters(name = "additionalQuickfixProviderAvailable={0}")
    fun parameters() = listOf(
      arrayOf(true),
      arrayOf(false),
    )
  }

  @Parameter
  @JvmField
  var additionalQuickfixProviderAvailable: Boolean? = null

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private lateinit var testDataPath: String

  private lateinit var basePath: String
  private lateinit var myTaskId: ExternalSystemTaskId

  private lateinit var scheduler: VirtualTimeScheduler
  private lateinit var myTracker: TestUsageTracker
  private lateinit var buildViewTestFixture: BuildViewTestFixture

  @Mock
  private lateinit var myFileDocumentManager: FileDocumentManager

  @Before
  fun setUp() {
    testDataPath = AndroidTestBase.getModulePath("project-system-gradle") + "/testData/buildOutput"
    MockitoAnnotations.initMocks(this)
    scheduler = VirtualTimeScheduler()
    myTracker = TestUsageTracker(scheduler)
    UsageTracker.setWriterForTest(myTracker)

    basePath = FileUtil.toSystemDependentName(projectRule.project.basePath!!)
    myTaskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, projectRule.project)

    buildViewTestFixture = BuildViewTestFixture(projectRule.project)
    buildViewTestFixture.setUp()

    val gradleErrorQuickFixProvider = object : GradleErrorQuickFixProvider {
      override fun createBuildIssueAdditionalQuickFix(buildEvent: BuildEvent, taskId: ExternalSystemTaskId): DescribedBuildIssueQuickFix? {
        if (additionalQuickfixProviderAvailable != true) return null
        if (buildEvent !is MessageEvent) return null
        return object: DescribedBuildIssueQuickFix {
          override val description: String
            get() = "Additional quickfix link"
          override val id: String
            get() = "com.plugin.gradle.quickfix"
        }
      }

      override fun createSyncMessageAdditionalLink(syncMessage: SyncMessage,
                                                   affectedModules: List<Module>,
                                                   buildFileMap: Map<Module, VirtualFile>,
                                                   rootProjectPath: @SystemIndependent String): SyncMessageHyperlink? {
        error("Should not be called in this test")
      }
    }
    ApplicationManager.getApplication()
      .registerExtension(GradleErrorQuickFixProvider.EP_NAME, gradleErrorQuickFixProvider, projectRule.testRootDisposable)
  }

  @After
  fun tearDown() {
    if (::buildViewTestFixture.isInitialized) buildViewTestFixture.tearDown()
    myTracker.close()
    UsageTracker.cleanAfterTesting()
  }

  private fun replayGradleOutput(myTaskId: ExternalSystemTaskId, output: String, exception: Exception?) {
    val myRequest = GradleBuildInvoker.Request.builder(projectRule.project, File(basePath)).setTaskId(myTaskId).build()
    val buildListener =
      GradleBuildInvokerImpl.Companion.createBuildTaskListenerForTests(
        projectRule.project,
        myFileDocumentManager,
        myRequest,
        ""
      )
    buildListener.onStart(basePath, myTaskId)
    output.lines().forEach { line ->
      if (line.startsWith("> Task :")) {
        // Send out task start and task finish events. GradleOutputMessageDispatcher creates sub-readers for each task,
        // which work in parallel, and it is important to mimic this process fully.
        val taskName = line.removePrefix("> Task ").substringBefore(' ')
        val eventId = "[root build id] > [Task $taskName]"
        //See org.jetbrains.plugins.gradle.service.execution.GradleProgressEventConverter.convertTaskProgressEventResult for real conversion
        val result = when {
          line.endsWith(" FAILED") -> FailureResultImpl(null, null)
          line.endsWith(" UP-TO-DATE") -> SuccessResultImpl(/* isUpToDate = */ true)
          else -> SuccessResultImpl(/* isUpToDate = */ false)
        }
        buildListener.onStatusChange(
          ExternalSystemBuildEvent(myTaskId, StartEventImpl(eventId, myTaskId, System.currentTimeMillis(), taskName))
        )
        buildListener.onStatusChange(
          ExternalSystemBuildEvent(myTaskId, FinishEventImpl(eventId, myTaskId, System.currentTimeMillis(), taskName, result))
        )
      }
    }
    buildListener.onTaskOutput(myTaskId, output, true)
    if (exception != null) {
      buildListener.onFailure(basePath, myTaskId, exception)
    }
    else {
      buildListener.onSuccess(basePath, myTaskId)
    }
    buildListener.onEnd(basePath, myTaskId)
  }

  private fun List<BuildErrorMessage>.checkSentMetricsData(assertions: List<(BuildErrorMessage) -> Unit>) {
    assertThat(this).hasSize(assertions.size)
    this.zip(assertions).forEach { it.second.invoke(it.first) }
  }

  private fun BuildErrorMessage.checkBuildErrorMessage(
    errorType: BuildErrorMessage.ErrorType,
    fileType: BuildErrorMessage.FileType,
    fileIncluded: Boolean,
    lineIncluded: Boolean
  ) {
    assertThat(errorShownType).isEquivalentAccordingToCompareTo(errorType)
    assertThat(fileIncludedType).isEquivalentAccordingToCompareTo(fileType)
    assertThat(fileLocationIncluded).isEqualTo(fileIncluded)
    assertThat(lineLocationIncluded).isEqualTo(lineIncluded)
  }

  private fun getMatchingNodesConsoleContent(
    nameFilter: Predicate<String>
  ): String {
    val buildView = projectRule.project.getService(BuildViewManager::class.java).getBuildView(myTaskId)!!
    val eventView = buildView.getView(BuildTreeConsoleView::class.java.name, BuildTreeConsoleView::class.java)
    eventView!!.addFilter { true }
    val tree = eventView.tree
    val nodes = runInEdtAndGet {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.waitWhileBusy(tree)

      TreeUtil.treeNodeTraverser(tree.model.root as DefaultMutableTreeNode).filter {
        val userObject = (it as DefaultMutableTreeNode).userObject
        userObject is ExecutionNode && nameFilter.test(userObject.name)
      }.toList()
    }

    val result = mutableListOf<String>()

    nodes.forEach { node ->
      val selectedPathComponent =
        if (node != tree.selectionPath?.lastPathComponent) {
          runInEdtAndGet {
            TreeUtil.selectNode(tree, node)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            PlatformTestUtil.waitWhileBusy(tree)
            tree.selectionPath!!.lastPathComponent
          }
        }
        else {
          tree.selectionPath!!.lastPathComponent
        }
      if (node != selectedPathComponent) {
        assertEquals(node.toString(), selectedPathComponent.toString())
      }
      val selectedNodeConsole = runInEdtAndGet { eventView.selectedNodeConsole }

      val executionNode = (node as DefaultMutableTreeNode).userObject as ExecutionNode
      val kind = when {
        executionNode.isFailed -> "ERROR"
        executionNode.hasWarnings() -> "WARNING"
        else -> ""
      }

      result.add(buildString {
        appendLine(tree.selectionPath!!.path.joinToString(prefix = "Path:", separator = " > ") { it.toString() })
        appendLine(kind)
        appendLine((selectedNodeConsole as? ConsoleViewImpl)?.text)
        append("---")
      })
    }
    return result.joinToString(separator = "\n")
  }

  private fun File.readTestFile(fileName: String, replacements: List<Pair<String, String>> = emptyList()): String {
    val file = this.resolve(fileName)
    var output = FileUtil.loadFile(file)
    replacements.forEach { (from, to) -> output = output.replace(from, to) }
    return output
  }

  private fun runChecks(
    expectedTreeStructure: String,
    consoleContentCheckers: List<Pair<Predicate<String>, String>>,
    buildErrorMessageAssertions: List<(BuildErrorMessage) -> Unit>?
  ) {
    // Check BuildOutputWindow messages
    buildViewTestFixture.assertBuildViewTreeEquals(expectedTreeStructure)

    consoleContentCheckers.forEach { (consoleFilter, expectedConsoleContent) ->
      val consolesDump = getMatchingNodesConsoleContent(consoleFilter)
      assertThat(consolesDump).isEqualTo(expectedConsoleContent)
    }

    val buildOutputWindowEvents = myTracker.usages.filter {
      it.studioEvent.hasBuildOutputWindowStats()
    }

    if (buildErrorMessageAssertions == null) {
      assertThat(buildOutputWindowEvents).isEmpty()
    }
    else {
      assertThat(buildOutputWindowEvents).hasSize(1)
      buildOutputWindowEvents.first().studioEvent.buildOutputWindowStats.buildErrorMessagesList.checkSentMetricsData(buildErrorMessageAssertions)
    }
  }

  @Test
  fun testAndroidGradlePluginErrors() {
    AssumeUtil.assumeNotWindows() // TODO (b/399625141): fix on windows
    val path = File(basePath, "styles.xml")
    val absolutePath = path.absolutePath
    val testDir = File(FileUtil.toSystemDependentName(testDataPath)).resolve("androidGradlePluginErrors")
    val gradleOutput = testDir.readTestFile("gradleOutput.txt", listOf("\$absolutePath" to absolutePath))
    //TODO (b/372180686): extra `Android resource linking failed` message is currently generated
    val expectedTreeStructure = testDir.readTestFile("expectedTreeStructure.txt")
    val expectedConsoleContent = if (additionalQuickfixProviderAvailable == true) {
      testDir.readTestFile("expectedConsoleContent-withQuickfixProvider.txt", listOf("\$stylesXmlPath" to absolutePath))
    }
    else {
      testDir.readTestFile("expectedConsoleContent.txt", listOf("\$stylesXmlPath" to absolutePath))
    }

    replayGradleOutput(
      myTaskId = myTaskId,
      output = gradleOutput,
      exception = RuntimeException("fake exception for test")
    )

    runChecks(
      expectedTreeStructure = expectedTreeStructure,
      consoleContentCheckers = listOf(
        Predicate<String> { it == "Android resource linking failed" } to expectedConsoleContent
      ),
      buildErrorMessageAssertions = listOf(
        { it.checkBuildErrorMessage(AAPT, PROJECT_FILE, fileIncluded = true, lineIncluded = true) },
        { it.checkBuildErrorMessage(AAPT, PROJECT_FILE, fileIncluded = true, lineIncluded = true) },
        { it.checkBuildErrorMessage(AAPT, PROJECT_FILE, fileIncluded = true, lineIncluded = true) },
        { it.checkBuildErrorMessage(AAPT, PROJECT_FILE, fileIncluded = true, lineIncluded = true) },
        { it.checkBuildErrorMessage(UNKNOWN_ERROR_TYPE, UNKNOWN_FILE_TYPE, fileIncluded = false, lineIncluded = false) },
      )
    )
  }

  @Test
  fun testXmlParsingError() {
    val path = File(basePath, "AndroidManifest.xml").absolutePath
    val testDir = File(FileUtil.toSystemDependentName(testDataPath)).resolve("xmlParsingError")
    val gradleOutput = testDir.readTestFile("gradleOutput.txt", listOf("\$path" to path))
    val expectedTreeStructure = testDir.readTestFile("expectedTreeStructure.txt")
    val expectedConsoleContent = if (additionalQuickfixProviderAvailable == true) {
      testDir.readTestFile("expectedConsoleContent-withQuickfixProvider.txt", listOf("\$basePath" to basePath))
    }
    else {
      testDir.readTestFile("expectedConsoleContent.txt", listOf("\$basePath" to basePath))
    }

    replayGradleOutput(
      myTaskId = myTaskId,
      output = gradleOutput,
      exception = RuntimeException("fake exception for test")
    )

    runChecks(
      expectedTreeStructure = expectedTreeStructure,
      consoleContentCheckers = listOf(
        Predicate<String> { it.startsWith("Attribute name ") } to expectedConsoleContent
      ),
      buildErrorMessageAssertions = listOf(
        { it.checkBuildErrorMessage(XML_PARSER, UNKNOWN_FILE_TYPE, fileIncluded = false, lineIncluded = false) },
      )
    )
  }

  @Test
  fun testSyncXmlParseErrors() {
    val testDir = File(FileUtil.toSystemDependentName(testDataPath)).resolve("xmlParsingErrorsDuringSync")
    val gradleOutput = testDir.readTestFile("gradleOutput.txt")
    val expectedTreeStructure = testDir.readTestFile("expectedTreeStructure.txt")
    val expectedConsoleContent = if (additionalQuickfixProviderAvailable == true) {
      testDir.readTestFile("expectedConsoleContent-withQuickfixProvider.txt", listOf("\$basePath" to basePath))
    }
    else {
      testDir.readTestFile("expectedConsoleContent.txt", listOf("\$basePath" to basePath))
    }

    replayGradleOutput(
      myTaskId = myTaskId,
      output = gradleOutput,
      exception = null
    )

    runChecks(
      expectedTreeStructure = expectedTreeStructure,
      consoleContentCheckers = listOf(
        Predicate<String> { it.startsWith("cvc-complex-type.2.4.") } to expectedConsoleContent
      ),
      buildErrorMessageAssertions = null // We don't report anything if it is not a failure
    )
  }
}
