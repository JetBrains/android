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

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.build.internal.DummyBuildViewManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BuildOutputParsersIntegrationTest : HeavyPlatformTestCase() {
  private lateinit var myTaskId: ExternalSystemTaskId

  private lateinit var scheduler: VirtualTimeScheduler
  private lateinit var myTracker: TestUsageTracker
  private lateinit var myRequest: GradleBuildInvoker.Request

  val allBuildEventsProcessedLatch = CountDownLatch(1)

  @Mock
  private lateinit var myFileDocumentManager: FileDocumentManager

  override fun setUp() {
    super.setUp()
    MockitoAnnotations.initMocks(this)
    scheduler = VirtualTimeScheduler()
    myTracker = TestUsageTracker(scheduler)
    UsageTracker.setWriterForTest(myTracker)

    myTaskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myProject)

    myRequest = GradleBuildInvoker.Request.builder(myProject, File(myProject.basePath)).setTaskId(myTaskId).build()
    val viewListeners = DisposableWrapperList<BuildProgressListener>()

    project.replaceService(BuildViewManager::class.java, object : DummyBuildViewManager(project) {

      override fun addListener(listener: BuildProgressListener, disposable: Disposable) {
        viewListeners.add(listener, disposable)
      }

      override fun onEvent(buildId: Any, event: BuildEvent) {
        viewListeners.forEach {
          it.onEvent(buildId, event)
        }
        if (event is FinishBuildEventImpl) {
          allBuildEventsProcessedLatch.countDown()
        }
      }
    }, testRootDisposable)
  }

  override fun tearDown() {
    myTracker.close()
    UsageTracker.cleanAfterTesting()
    super.tearDown()
  }

  private fun checkSentMetricsData(sentMetricsData: BuildErrorMessage,
                                   errorType: BuildErrorMessage.ErrorType,
                                   fileType: BuildErrorMessage.FileType,
                                   fileIncluded: Boolean,
                                   lineIncluded: Boolean) {
    assertThat(sentMetricsData).isNotNull()
    assertThat(sentMetricsData.errorShownType).isEquivalentAccordingToCompareTo(errorType)
    assertThat(sentMetricsData.fileIncludedType).isEquivalentAccordingToCompareTo(fileType)
    assertThat(sentMetricsData.fileLocationIncluded).isEqualTo(fileIncluded)
    assertThat(sentMetricsData.lineLocationIncluded).isEqualTo(lineIncluded)
  }

  @Test
  fun testAndroidGradlePluginErrors() {
    val buildListener =
      GradleBuildInvokerImpl.Companion.createBuildTaskListenerForTests(
        project,
        myFileDocumentManager,
        myRequest,
        ""
      )
    val path = tempDir.newPath("styles.xml")
    val absolutePath = StringUtil.escapeBackSlashes(path.toAbsolutePath().toString())
    val output = """
                    Executing tasks: [clean, :app:assembleDebug]
                    > Task :clean UP-TO-DATE
                    > Task :app:clean
                    > Task :app:preBuild UP-TO-DATE
                    > Task :app:extractProguardFiles
                    > Task :app:preDebugBuild
                    > Task :app:checkDebugManifest
                    > Task :app:generateDebugBuildConfig
                    > Task :app:mainApkListPersistenceDebug
                    > Task :app:generateDebugResValues
                    > Task :app:createDebugCompatibleScreenManifests
                    > Task :app:mergeDebugShaders
                    > Task :app:compileDebugShaders
                    > Task :app:compileDebugAidl NO-SOURCE
                    > Task :app:compileDebugRenderscript NO-SOURCE
                    > Task :app:generateDebugResources
                    > Task :app:processDebugManifest
                    > Task :app:generateDebugAssets
                    > Task :app:mergeDebugAssets
                    > Task :app:validateSigningDebug
                    > Task :app:signingConfigWriterDebug
                    > Task :app:mergeDebugResources

                    > Task :app:processDebugResources FAILED
                    AGPBI: {"kind":"error","text":"Android resource linking failed","sources":[{"file":"$absolutePath","position":{"startLine":3,"startColumn":4,"startOffset":54,"endLine":14,"endColumn":12,"endOffset":686}}],"original":"$absolutePath:4:5-15:13: AAPT: error: style attribute 'attr/colorPrfimary (aka com.example.myapplication:attr/colorPrfimary)' not found.\n    ","tool":"AAPT"}
                    AGPBI: {"kind":"error","text":"Android resource linking failed","sources":[{"file":"$absolutePath","position":{"startLine":3,"startColumn":4,"startOffset":54,"endLine":14,"endColumn":12,"endOffset":686}}],"original":"$absolutePath:4:5-15:13: AAPT: error: style attribute 'attr/colorPgfrimaryDark (aka com.example.myapplication:attr/colorPgfrimaryDark)' not found.\n    ","tool":"AAPT"}
                    AGPBI: {"kind":"error","text":"Android resource linking failed","sources":[{"file":"$absolutePath","position":{"startLine":3,"startColumn":4,"startOffset":54,"endLine":14,"endColumn":12,"endOffset":686}}],"original":"$absolutePath:4:5-15:13: AAPT: error: style attribute 'attr/dfg (aka com.example.myapplication:attr/dfg)' not found.\n    ","tool":"AAPT"}
                    AGPBI: {"kind":"error","text":"Android resource linking failed","sources":[{"file":"$absolutePath","position":{"startLine":3,"startColumn":4,"startOffset":54,"endLine":14,"endColumn":12,"endOffset":686}}],"original":"$absolutePath:4:5-15:13: AAPT: error: style attribute 'attr/colorEdfdrror (aka com.example.myapplication:attr/colorEdfdrror)' not found.\n    ","tool":"AAPT"}

                    FAILURE: Build failed with an exception.

                    * What went wrong:
                    Execution failed for task ':app:processDebugResources'.
                    > A failure occurred while executing com.android.build.gradle.internal.tasks.Workers.ActionFacade
                       > Android resource linking failed
                         $absolutePath:4:5-15:13: AAPT: error: style attribute 'attr/colorPrfimary (aka com.example.myapplication:attr/colorPrfimary)' not found.

                         $absolutePath:4:5-15:13: AAPT: error: style attribute 'attr/colorPgfrimaryDark (aka com.example.myapplication:attr/colorPgfrimaryDark)' not found.

                         $absolutePath:4:5-15:13: AAPT: error: style attribute 'attr/dfg (aka com.example.myapplication:attr/dfg)' not found.

                         $absolutePath:4:5-15:13: AAPT: error: style attribute 'attr/colorEdfdrror (aka com.example.myapplication:attr/colorEdfdrror)' not found.


                    * Try:
                    Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output. Run with --scan to get full insights.

                    * Get more help at https://help.gradle.org

                    BUILD FAILED in 5s
                    16 actionable tasks: 15 executed, 1 up-to-date""".trimIndent()

    buildListener.onStart(myTaskId, project.basePath)
    buildListener.onTaskOutput(myTaskId, output, true)
    buildListener.onFailure(myTaskId, RuntimeException("test"))
    buildListener.onEnd(myTaskId)

    allBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)

    val buildOutputWindowEvents = myTracker.usages.filter {
      it.studioEvent.hasBuildOutputWindowStats()
    }

    assertThat(buildOutputWindowEvents).hasSize(1)

    val messages = buildOutputWindowEvents.first().studioEvent.buildOutputWindowStats.buildErrorMessagesList
    assertThat(messages).hasSize(4)

    messages.forEach {
      checkSentMetricsData(it, BuildErrorMessage.ErrorType.AAPT, BuildErrorMessage.FileType.PROJECT_FILE,
                           fileIncluded = true, lineIncluded = true)
    }
  }

  @Test
  fun testXmlParsingError() {
    val buildListener =
      GradleBuildInvokerImpl.Companion.createBuildTaskListenerForTests(
        project,
        myFileDocumentManager,
        myRequest,
        ""
      )
    val file = tempDir.createVirtualFile("AndroidManifest.xml")
    val path = file.toNioPath()
    val output = """Executing tasks: [clean, :app:assembleDebug]
                    > Configure project :app
                    > Task :clean UP-TO-DATE
                    > Task :app:clean
                    > Task :app:preBuild UP-TO-DATE
                    > Task :app:extractProguardFiles
                    > Task :app:preDebugBuild
                    > Task :app:checkDebugManifest
                    > Task :app:generateDebugBuildConfig FAILED

                    FAILURE: Build failed with an exception.

                    * What went wrong:
                    Execution failed for task ':app:generateDebugBuildConfig'.
                    > org.xml.sax.SAXParseException; systemId: file:${path.toAbsolutePath()}; lineNumber: 9; columnNumber: 1; Attribute name "sd" associated with an element type "Dsfsd" must be followed by the ' = ' character.

                    * Try:
                    Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output. Run with --scan to get full insights.

                    * Get more help at https://help.gradle.org

                    BUILD FAILED in 0s
                    5 actionable tasks: 4 executed, 1 up-to-date""".trimIndent()

    buildListener.onStart(myTaskId, project.basePath)
    buildListener.onTaskOutput(myTaskId, output, true)
    buildListener.onFailure(myTaskId, RuntimeException("test"))
    buildListener.onEnd(myTaskId)

    allBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)

    val buildOutputWindowEvents = myTracker.usages.filter {
      it.studioEvent.hasBuildOutputWindowStats()
    }

    assertThat(buildOutputWindowEvents).hasSize(1)

    val messages = buildOutputWindowEvents.first().studioEvent.buildOutputWindowStats.buildErrorMessagesList
    assertThat(messages).isNotNull()
    assertThat(messages).hasSize(1)

    checkSentMetricsData(messages.first(), BuildErrorMessage.ErrorType.XML_PARSER, BuildErrorMessage.FileType.PROJECT_FILE,
                         fileIncluded = true, lineIncluded = true)

  }
}
