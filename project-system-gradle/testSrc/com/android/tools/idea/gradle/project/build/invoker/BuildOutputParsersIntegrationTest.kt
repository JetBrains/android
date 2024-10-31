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
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenStudioBotBuildIssueQuickFix
import com.android.tools.idea.studiobot.StudioBot
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.build.internal.DummyBuildViewManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BuildOutputParsersIntegrationTest : HeavyPlatformTestCase() {
  private lateinit var myProjectPath: String
  private lateinit var myTaskId: ExternalSystemTaskId

  private lateinit var scheduler: VirtualTimeScheduler
  private lateinit var myTracker: TestUsageTracker
  private lateinit var myRequest: GradleBuildInvoker.Request
  private val buildEvents = ContainerUtil.createConcurrentList<BuildEvent>()

  val allBuildEventsProcessedLatch = CountDownLatch(1)

  @Mock
  private lateinit var myFileDocumentManager: FileDocumentManager

  override fun setUp() {
    super.setUp()
    MockitoAnnotations.initMocks(this)
    scheduler = VirtualTimeScheduler()
    myTracker = TestUsageTracker(scheduler)
    UsageTracker.setWriterForTest(myTracker)

    myProjectPath = myProject.basePath!!
    myTaskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myProject)

    myRequest = GradleBuildInvoker.Request.builder(myProject, File(myProjectPath)).setTaskId(myTaskId).build()
    val viewListeners = DisposableWrapperList<BuildProgressListener>()

    project.replaceService(BuildViewManager::class.java, object : DummyBuildViewManager(project) {

      override fun addListener(listener: BuildProgressListener, disposable: Disposable) {
        viewListeners.add(listener, disposable)
      }

      override fun onEvent(buildId: Any, event: BuildEvent) {
        viewListeners.forEach {
          it.onEvent(buildId, event)
        }
        buildEvents.add(event)
        if (event is FinishBuildEventImpl) {
          allBuildEventsProcessedLatch.countDown()
        }
      }
    }, testRootDisposable)
  }

  override fun tearDown() {
    myTracker.close()
    UsageTracker.cleanAfterTesting()
    buildEvents.clear()
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

  fun testAndroidGradlePluginErrors() {
    val buildListener =
      GradleBuildInvokerImpl.Companion.createBuildTaskListenerForTests(
        project,
        myFileDocumentManager,
        myRequest,
        ""
      )
    buildListener.onStart(myProjectPath, myTaskId)
    buildListener.onTaskOutput(myTaskId, getOutputWithAndroidGradlePluginErrors(), true)
    buildListener.onFailure(myProjectPath, myTaskId, RuntimeException("test"))
    buildListener.onEnd(myProjectPath, myTaskId)

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

  fun testAndroidGradlePluginErrors_whenStudioBotIsEnabled_addsQuickFixLinks() {
    // Given: StudioBot is enabled.
    setStudioBotInstanceAvailability(true)

    // When: BuildTaskListener listens to the Gradle task output.
    val buildListener =
      GradleBuildInvokerImpl.Companion.createBuildTaskListenerForTests(
        project,
        myFileDocumentManager,
        myRequest,
        ""
      )
    buildListener.onStart(myTaskId, project.basePath)
    buildListener.onTaskOutput(myTaskId, getOutputWithAndroidGradlePluginErrors(), true)
    buildListener.onFailure(myTaskId, RuntimeException("test"))
    buildListener.onEnd(myTaskId)
    allBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)

    // Then: Assert that Ask Gemini quick fix links are added to FileMessageEvents.
    assertThat(buildEvents.filterIsInstance<FileMessageEvent>()).isNotEmpty()
    buildEvents.filterIsInstance<FileMessageEvent>(). forEach { fileMessageEvent ->
      val buildIssueEvent = fileMessageEvent as? BuildIssueEvent
      assertTrue(buildIssueEvent?.issue?.quickFixes?.any { it is OpenStudioBotBuildIssueQuickFix}?:false)
    }
    // And: Assert that Ask Gemini quick fix links are added to MessageEvents.
    assertThat(buildEvents.filterIsInstance<MessageEvent>()).isNotEmpty()
    buildEvents.filterIsInstance<MessageEvent>(). forEach { messageEvent ->
      val buildIssueEvent = messageEvent as? BuildIssueEvent
      assertTrue(buildIssueEvent?.issue?.quickFixes?.any { it is OpenStudioBotBuildIssueQuickFix}?:false)
    }
  }

  private fun setStudioBotInstanceAvailability(isAvailable: Boolean) {
    val studioBot = object : StudioBot.StubStudioBot() {
      override fun isAvailable(): Boolean = isAvailable
    }
    ApplicationManager.getApplication()
      .replaceService(StudioBot::class.java, studioBot, project)
  }


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
    val output = """
Executing tasks: [clean, :app:assembleDebug]
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
5 actionable tasks: 4 executed, 1 up-to-date
"""

    buildListener.onStart(myProjectPath, myTaskId)
    buildListener.onTaskOutput(myTaskId, output, true)
    buildListener.onFailure(myProjectPath, myTaskId, RuntimeException("test"))
    buildListener.onEnd(myProjectPath, myTaskId)

    allBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)

    // Check BuildOutputWindow messages
    assertThat(buildEvents.filterIsInstance<MessageEvent>().joinToString(separator = "\n") { "${it.kind}: ${it.message}" })
      .isEqualTo("ERROR: Attribute name \"sd\" associated with an element type \"Dsfsd\" must be followed by the ' = ' character.")

    // Check metrics
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

  fun testSyncXmlParseErrors() {
    val output = """
Starting Gradle Daemon...
Gradle Daemon started in 815 ms

> Configure project :
This is a simple application!

> Configure project :app
Mapping new ns http://schemas.android.com/repository/android/common/02 to old ns http://schemas.android.com/repository/android/common/01
Mapping new ns http://schemas.android.com/repository/android/generic/02 to old ns http://schemas.android.com/repository/android/generic/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/02 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/02 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/02 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/repository/android/common/02 to old ns http://schemas.android.com/repository/android/common/01
Mapping new ns http://schemas.android.com/repository/android/generic/02 to old ns http://schemas.android.com/repository/android/generic/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/02 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/03 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/02 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/03 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/03 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/02 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/repository/android/common/02 to old ns http://schemas.android.com/repository/android/common/01
Mapping new ns http://schemas.android.com/repository/android/generic/02 to old ns http://schemas.android.com/repository/android/generic/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/02 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/03 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/02 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/03 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/03 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/02 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/repository/android/common/02 to old ns http://schemas.android.com/repository/android/common/01
Mapping new ns http://schemas.android.com/repository/android/generic/02 to old ns http://schemas.android.com/repository/android/generic/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/02 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/03 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/02 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/03 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/04 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/03 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/02 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/repository/android/common/02 to old ns http://schemas.android.com/repository/android/common/01
Mapping new ns http://schemas.android.com/repository/android/generic/02 to old ns http://schemas.android.com/repository/android/generic/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/02 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/03 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/02 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/03 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/04 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/03 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/02 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/repository/android/common/02 to old ns http://schemas.android.com/repository/android/common/01
Mapping new ns http://schemas.android.com/repository/android/generic/02 to old ns http://schemas.android.com/repository/android/generic/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/02 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/03 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/02 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/03 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/04 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/03 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/02 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/repository/android/common/02 to old ns http://schemas.android.com/repository/android/common/01
Mapping new ns http://schemas.android.com/repository/android/generic/02 to old ns http://schemas.android.com/repository/android/generic/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/02 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/03 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/02 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/03 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/03 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/02 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
cvc-complex-type.2.4.a: Invalid content was found starting with element 'extension-level'. One of '{layoutlib}' is expected.:
org.xml.sax.SAXParseException; lineNumber: 141; columnNumber: 253; cvc-complex-type.2.4.a: Invalid content was found starting with element 'extension-level'. One of '{layoutlib}' is expected.
  at java.xml/com.sun.org.apache.xerces.internal.util.ErrorHandlerWrapper.createSAXParseException(ErrorHandlerWrapper.java:204)
  at java.xml/com.sun.org.apache.xerces.internal.util.ErrorHandlerWrapper.error(ErrorHandlerWrapper.java:135)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:396)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:327)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:284)
  at java.xml/com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator${'$'}XSIErrorReporter.reportError(XMLSchemaValidator.java:511)
  at java.xml/com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator.reportSchemaError(XMLSchemaValidator.java:3599)
  at java.xml/com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator.handleStartElement(XMLSchemaValidator.java:1971)
  at java.xml/com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator.startElement(XMLSchemaValidator.java:829)
  at java.xml/com.sun.org.apache.xerces.internal.jaxp.validation.ValidatorHandlerImpl.startElement(ValidatorHandlerImpl.java:570)
  at com.sun.xml.bind.v2.runtime.unmarshaller.ValidatingUnmarshaller.startElement(ValidatingUnmarshaller.java:101)
  at com.sun.xml.bind.v2.runtime.unmarshaller.SAXConnector.startElement(SAXConnector.java:168)
  at java.xml/org.xml.sax.helpers.XMLFilterImpl.startElement(XMLFilterImpl.java:551)
  at com.android.repository.impl.meta.SchemaModuleUtil${'$'}NamespaceFallbackFilter.startElement(SchemaModuleUtil.java:403)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.AbstractSAXParser.startElement(AbstractSAXParser.java:510)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl.scanStartElement(XMLNSDocumentScannerImpl.java:374)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl${'$'}FragmentContentDriver.next(XMLDocumentFragmentScannerImpl.java:2710)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl.next(XMLDocumentScannerImpl.java:605)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl.next(XMLNSDocumentScannerImpl.java:112)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl.scanDocument(XMLDocumentFragmentScannerImpl.java:534)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.XML11Configuration.parse(XML11Configuration.java:888)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.XML11Configuration.parse(XML11Configuration.java:824)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.XMLParser.parse(XMLParser.java:141)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.AbstractSAXParser.parse(AbstractSAXParser.java:1216)
  at java.xml/com.sun.org.apache.xerces.internal.jaxp.SAXParserImpl${'$'}JAXPSAXParser.parse(SAXParserImpl.java:635)
  at java.xml/org.xml.sax.helpers.XMLFilterImpl.parse(XMLFilterImpl.java:357)
  at com.sun.xml.bind.v2.runtime.unmarshaller.UnmarshallerImpl.unmarshal0(UnmarshallerImpl.java:258)
  at com.sun.xml.bind.v2.runtime.unmarshaller.UnmarshallerImpl.unmarshal(UnmarshallerImpl.java:229)
  at javax.xml.bind.helpers.AbstractUnmarshallerImpl.unmarshal(AbstractUnmarshallerImpl.java:155)
  at javax.xml.bind.helpers.AbstractUnmarshallerImpl.unmarshal(AbstractUnmarshallerImpl.java:138)
  at com.android.repository.impl.meta.SchemaModuleUtil.unmarshal(SchemaModuleUtil.java:196)
  at com.android.repository.impl.manager.LocalRepoLoaderImpl.parsePackage(LocalRepoLoaderImpl.java:305)
  at com.android.repository.impl.manager.LocalRepoLoaderImpl.parsePackages(LocalRepoLoaderImpl.java:159)
  at com.android.repository.impl.manager.LocalRepoLoaderImpl.getPackages(LocalRepoLoaderImpl.java:124)
  at com.android.repository.impl.manager.RepoManagerImpl${'$'}LoadTask.run(RepoManagerImpl.java:518)
  at com.android.repository.api.RepoManager${'$'}DummyProgressRunner.runSyncWithProgress(RepoManager.java:397)
  at com.android.repository.impl.manager.RepoManagerImpl.load(RepoManagerImpl.java:365)
  at com.android.repository.api.RepoManager.loadSynchronously(RepoManager.java:290)
  at com.android.sdklib.repository.AndroidSdkHandler${'$'}RepoConfig.createRepoManager(AndroidSdkHandler.java:731)
  at com.android.sdklib.repository.AndroidSdkHandler.getSdkManager(AndroidSdkHandler.java:297)
  at com.android.builder.sdk.DefaultSdkLoader.init(DefaultSdkLoader.java:393)
  at com.android.builder.sdk.DefaultSdkLoader.getSdkInfo(DefaultSdkLoader.java:374)
  at com.android.build.gradle.internal.SdkHandler.initTarget(SdkHandler.java:179)
  at com.android.build.gradle.BasePlugin.ensureTargetSetup(BasePlugin.java:980)
  at com.android.build.gradle.BasePlugin.createAndroidTasks(BasePlugin.java:752)
  at com.android.build.gradle.BasePlugin.lambda${'$'}null${'$'}4(BasePlugin.java:690)
  at com.android.builder.profile.ThreadRecorder.record(ThreadRecorder.java:81)
  at com.android.build.gradle.BasePlugin.lambda${'$'}createTasks${'$'}5(BasePlugin.java:686)
  at org.gradle.configuration.internal.DefaultListenerBuildOperationDecorator${'$'}BuildOperationEmittingAction${'$'}1${'$'}1.run(DefaultListenerBuildOperationDecorator.java:157)
  at org.gradle.configuration.internal.DefaultUserCodeApplicationContext.reapply(DefaultUserCodeApplicationContext.java:58)
  at org.gradle.configuration.internal.DefaultListenerBuildOperationDecorator${'$'}BuildOperationEmittingAction${'$'}1.run(DefaultListenerBuildOperationDecorator.java:154)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.configuration.internal.DefaultListenerBuildOperationDecorator${'$'}BuildOperationEmittingAction.execute(DefaultListenerBuildOperationDecorator.java:151)
  at org.gradle.internal.event.BroadcastDispatch${'$'}ActionInvocationHandler.dispatch(BroadcastDispatch.java:91)
  at org.gradle.internal.event.BroadcastDispatch${'$'}ActionInvocationHandler.dispatch(BroadcastDispatch.java:80)
  at org.gradle.internal.event.AbstractBroadcastDispatch.dispatch(AbstractBroadcastDispatch.java:42)
  at org.gradle.internal.event.BroadcastDispatch${'$'}SingletonDispatch.dispatch(BroadcastDispatch.java:230)
  at org.gradle.internal.event.BroadcastDispatch${'$'}SingletonDispatch.dispatch(BroadcastDispatch.java:149)
  at org.gradle.internal.event.AbstractBroadcastDispatch.dispatch(AbstractBroadcastDispatch.java:58)
  at org.gradle.internal.event.BroadcastDispatch${'$'}CompositeDispatch.dispatch(BroadcastDispatch.java:324)
  at org.gradle.internal.event.BroadcastDispatch${'$'}CompositeDispatch.dispatch(BroadcastDispatch.java:234)
  at org.gradle.internal.event.ListenerBroadcast.dispatch(ListenerBroadcast.java:140)
  at org.gradle.internal.event.ListenerBroadcast.dispatch(ListenerBroadcast.java:37)
  at org.gradle.internal.dispatch.ProxyDispatchAdapter${'$'}DispatchingInvocationHandler.invoke(ProxyDispatchAdapter.java:93)
  at com.sun.proxy.${'$'}Proxy35.afterEvaluate(Unknown Source)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}NotifyAfterEvaluate${'$'}1.execute(LifecycleProjectEvaluator.java:191)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}NotifyAfterEvaluate${'$'}1.execute(LifecycleProjectEvaluator.java:188)
  at org.gradle.api.internal.project.DefaultProject.stepEvaluationListener(DefaultProject.java:1413)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}NotifyAfterEvaluate.run(LifecycleProjectEvaluator.java:197)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.internal.operations.DelegatingBuildOperationExecutor.run(DelegatingBuildOperationExecutor.java:31)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}EvaluateProject${'$'}1.run(LifecycleProjectEvaluator.java:112)
  at org.gradle.internal.Factories${'$'}1.create(Factories.java:25)
  at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:183)
  at org.gradle.internal.work.StopShieldingWorkerLeaseService.withLocks(StopShieldingWorkerLeaseService.java:40)
  at org.gradle.api.internal.project.DefaultProjectStateRegistry${'$'}ProjectStateImpl.withProjectLock(DefaultProjectStateRegistry.java:226)
  at org.gradle.api.internal.project.DefaultProjectStateRegistry${'$'}ProjectStateImpl.withMutableState(DefaultProjectStateRegistry.java:220)
  at org.gradle.api.internal.project.DefaultProjectStateRegistry${'$'}ProjectStateImpl.withMutableState(DefaultProjectStateRegistry.java:186)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}EvaluateProject.run(LifecycleProjectEvaluator.java:96)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.internal.operations.DelegatingBuildOperationExecutor.run(DelegatingBuildOperationExecutor.java:31)
  at org.gradle.configuration.project.LifecycleProjectEvaluator.evaluate(LifecycleProjectEvaluator.java:68)
  at org.gradle.api.internal.project.DefaultProject.evaluate(DefaultProject.java:687)
  at org.gradle.api.internal.project.DefaultProject.evaluate(DefaultProject.java:140)
  at org.gradle.execution.TaskPathProjectEvaluator.configure(TaskPathProjectEvaluator.java:35)
  at org.gradle.execution.TaskPathProjectEvaluator.configureHierarchy(TaskPathProjectEvaluator.java:62)
  at org.gradle.configuration.DefaultBuildConfigurer.configure(DefaultBuildConfigurer.java:41)
  at org.gradle.initialization.DefaultGradleLauncher${'$'}ConfigureBuild.run(DefaultGradleLauncher.java:286)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.internal.operations.DelegatingBuildOperationExecutor.run(DelegatingBuildOperationExecutor.java:31)
  at org.gradle.initialization.DefaultGradleLauncher.configureBuild(DefaultGradleLauncher.java:194)
  at org.gradle.initialization.DefaultGradleLauncher.doBuildStages(DefaultGradleLauncher.java:150)
  at org.gradle.initialization.DefaultGradleLauncher.executeTasks(DefaultGradleLauncher.java:133)
  at org.gradle.internal.invocation.GradleBuildController${'$'}1.execute(GradleBuildController.java:79)
  at org.gradle.internal.invocation.GradleBuildController${'$'}1.execute(GradleBuildController.java:76)
  at org.gradle.internal.invocation.GradleBuildController${'$'}3.create(GradleBuildController.java:103)
  at org.gradle.internal.invocation.GradleBuildController${'$'}3.create(GradleBuildController.java:96)
  at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:183)
  at org.gradle.internal.work.StopShieldingWorkerLeaseService.withLocks(StopShieldingWorkerLeaseService.java:40)
  at org.gradle.internal.invocation.GradleBuildController.doBuild(GradleBuildController.java:96)
  at org.gradle.internal.invocation.GradleBuildController.run(GradleBuildController.java:76)
  at org.gradle.tooling.internal.provider.runner.ClientProvidedPhasedActionRunner.run(ClientProvidedPhasedActionRunner.java:61)
  at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
  at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
  at org.gradle.tooling.internal.provider.ValidatingBuildActionRunner.run(ValidatingBuildActionRunner.java:32)
  at org.gradle.launcher.exec.RunAsBuildOperationBuildActionRunner${'$'}3.run(RunAsBuildOperationBuildActionRunner.java:49)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.internal.operations.DelegatingBuildOperationExecutor.run(DelegatingBuildOperationExecutor.java:31)
  at org.gradle.launcher.exec.RunAsBuildOperationBuildActionRunner.run(RunAsBuildOperationBuildActionRunner.java:44)
  at org.gradle.tooling.internal.provider.SubscribableBuildActionRunner.run(SubscribableBuildActionRunner.java:51)
  at org.gradle.launcher.exec.InProcessBuildActionExecuter${'$'}1.transform(InProcessBuildActionExecuter.java:47)
  at org.gradle.launcher.exec.InProcessBuildActionExecuter${'$'}1.transform(InProcessBuildActionExecuter.java:44)
  at org.gradle.composite.internal.DefaultRootBuildState.run(DefaultRootBuildState.java:79)
  at org.gradle.launcher.exec.InProcessBuildActionExecuter.execute(InProcessBuildActionExecuter.java:44)
  at org.gradle.launcher.exec.InProcessBuildActionExecuter.execute(InProcessBuildActionExecuter.java:30)
  at org.gradle.launcher.exec.BuildTreeScopeBuildActionExecuter.execute(BuildTreeScopeBuildActionExecuter.java:39)
  at org.gradle.launcher.exec.BuildTreeScopeBuildActionExecuter.execute(BuildTreeScopeBuildActionExecuter.java:25)
  at org.gradle.tooling.internal.provider.ContinuousBuildActionExecuter.execute(ContinuousBuildActionExecuter.java:78)
  at org.gradle.tooling.internal.provider.ContinuousBuildActionExecuter.execute(ContinuousBuildActionExecuter.java:52)
  at org.gradle.tooling.internal.provider.ServicesSetupBuildActionExecuter.execute(ServicesSetupBuildActionExecuter.java:62)
  at org.gradle.tooling.internal.provider.ServicesSetupBuildActionExecuter.execute(ServicesSetupBuildActionExecuter.java:34)
  at org.gradle.tooling.internal.provider.GradleThreadBuildActionExecuter.execute(GradleThreadBuildActionExecuter.java:36)
  at org.gradle.tooling.internal.provider.GradleThreadBuildActionExecuter.execute(GradleThreadBuildActionExecuter.java:25)
  at org.gradle.tooling.internal.provider.ParallelismConfigurationBuildActionExecuter.execute(ParallelismConfigurationBuildActionExecuter.java:43)
  at org.gradle.tooling.internal.provider.ParallelismConfigurationBuildActionExecuter.execute(ParallelismConfigurationBuildActionExecuter.java:29)
  at org.gradle.tooling.internal.provider.StartParamsValidatingActionExecuter.execute(StartParamsValidatingActionExecuter.java:59)
  at org.gradle.tooling.internal.provider.StartParamsValidatingActionExecuter.execute(StartParamsValidatingActionExecuter.java:31)
  at org.gradle.tooling.internal.provider.SessionFailureReportingActionExecuter.execute(SessionFailureReportingActionExecuter.java:59)
  at org.gradle.tooling.internal.provider.SessionFailureReportingActionExecuter.execute(SessionFailureReportingActionExecuter.java:44)
  at org.gradle.tooling.internal.provider.SetupLoggingActionExecuter.execute(SetupLoggingActionExecuter.java:46)
  at org.gradle.tooling.internal.provider.SetupLoggingActionExecuter.execute(SetupLoggingActionExecuter.java:30)
  at org.gradle.launcher.daemon.server.exec.ExecuteBuild.doBuild(ExecuteBuild.java:67)
  at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:36)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.WatchForDisconnection.execute(WatchForDisconnection.java:37)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.ResetDeprecationLogger.execute(ResetDeprecationLogger.java:26)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.RequestStopIfSingleUsedDaemon.execute(RequestStopIfSingleUsedDaemon.java:34)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.ForwardClientInput${'$'}2.call(ForwardClientInput.java:74)
  at org.gradle.launcher.daemon.server.exec.ForwardClientInput${'$'}2.call(ForwardClientInput.java:72)
  at org.gradle.util.Swapper.swap(Swapper.java:38)
  at org.gradle.launcher.daemon.server.exec.ForwardClientInput.execute(ForwardClientInput.java:72)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.LogAndCheckHealth.execute(LogAndCheckHealth.java:55)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.LogToClient.doBuild(LogToClient.java:62)
  at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:36)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.EstablishBuildEnvironment.doBuild(EstablishBuildEnvironment.java:81)
  at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:36)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.StartBuildOrRespondWithBusy${'$'}1.run(StartBuildOrRespondWithBusy.java:50)
  at org.gradle.launcher.daemon.server.DaemonStateCoordinator${'$'}1.run(DaemonStateCoordinator.java:295)
  at org.gradle.internal.concurrent.ExecutorPolicy${'$'}CatchAndRecordFailures.onExecute(ExecutorPolicy.java:63)
  at org.gradle.internal.concurrent.ManagedExecutorImpl${'$'}1.run(ManagedExecutorImpl.java:46)
  at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
  at java.base/java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(ThreadPoolExecutor.java:628)
  at org.gradle.internal.concurrent.ThreadFactoryImpl${'$'}ManagedThreadRunnable.run(ThreadFactoryImpl.java:55)
  at java.base/java.lang.Thread.run(Thread.java:829)

unexpected element (uri:"", local:"extension-level"). Expected elements are <{}codename>,<{}layoutlib>,<{}api-level>
unexpected element (uri:"", local:"base-extension"). Expected elements are <{}codename>,<{}layoutlib>,<{}api-level>
Mapping new ns http://schemas.android.com/repository/android/common/02 to old ns http://schemas.android.com/repository/android/common/01
Mapping new ns http://schemas.android.com/repository/android/generic/02 to old ns http://schemas.android.com/repository/android/generic/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/02 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/03 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/02 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/03 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/04 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/03 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/02 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
cvc-complex-type.2.4.d: Invalid content was found starting with element 'extension-level'. No child element is expected at this point.:
org.xml.sax.SAXParseException; lineNumber: 141; columnNumber: 249; cvc-complex-type.2.4.d: Invalid content was found starting with element 'extension-level'. No child element is expected at this point.
  at java.xml/com.sun.org.apache.xerces.internal.util.ErrorHandlerWrapper.createSAXParseException(ErrorHandlerWrapper.java:204)
  at java.xml/com.sun.org.apache.xerces.internal.util.ErrorHandlerWrapper.error(ErrorHandlerWrapper.java:135)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:396)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:327)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:284)
  at java.xml/com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator${'$'}XSIErrorReporter.reportError(XMLSchemaValidator.java:511)
  at java.xml/com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator.reportSchemaError(XMLSchemaValidator.java:3599)
  at java.xml/com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator.handleStartElement(XMLSchemaValidator.java:1988)
  at java.xml/com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator.startElement(XMLSchemaValidator.java:829)
  at java.xml/com.sun.org.apache.xerces.internal.jaxp.validation.ValidatorHandlerImpl.startElement(ValidatorHandlerImpl.java:570)
  at com.sun.xml.bind.v2.runtime.unmarshaller.ValidatingUnmarshaller.startElement(ValidatingUnmarshaller.java:101)
  at com.sun.xml.bind.v2.runtime.unmarshaller.SAXConnector.startElement(SAXConnector.java:168)
  at java.xml/org.xml.sax.helpers.XMLFilterImpl.startElement(XMLFilterImpl.java:551)
  at com.android.repository.impl.meta.SchemaModuleUtil${'$'}NamespaceFallbackFilter.startElement(SchemaModuleUtil.java:403)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.AbstractSAXParser.startElement(AbstractSAXParser.java:510)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl.scanStartElement(XMLNSDocumentScannerImpl.java:374)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl${'$'}FragmentContentDriver.next(XMLDocumentFragmentScannerImpl.java:2710)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl.next(XMLDocumentScannerImpl.java:605)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl.next(XMLNSDocumentScannerImpl.java:112)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl.scanDocument(XMLDocumentFragmentScannerImpl.java:534)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.XML11Configuration.parse(XML11Configuration.java:888)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.XML11Configuration.parse(XML11Configuration.java:824)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.XMLParser.parse(XMLParser.java:141)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.AbstractSAXParser.parse(AbstractSAXParser.java:1216)
  at java.xml/com.sun.org.apache.xerces.internal.jaxp.SAXParserImpl${'$'}JAXPSAXParser.parse(SAXParserImpl.java:635)
  at java.xml/org.xml.sax.helpers.XMLFilterImpl.parse(XMLFilterImpl.java:357)
  at com.sun.xml.bind.v2.runtime.unmarshaller.UnmarshallerImpl.unmarshal0(UnmarshallerImpl.java:258)
  at com.sun.xml.bind.v2.runtime.unmarshaller.UnmarshallerImpl.unmarshal(UnmarshallerImpl.java:229)
  at javax.xml.bind.helpers.AbstractUnmarshallerImpl.unmarshal(AbstractUnmarshallerImpl.java:155)
  at javax.xml.bind.helpers.AbstractUnmarshallerImpl.unmarshal(AbstractUnmarshallerImpl.java:138)
  at com.android.repository.impl.meta.SchemaModuleUtil.unmarshal(SchemaModuleUtil.java:196)
  at com.android.repository.impl.manager.LocalRepoLoaderImpl.parsePackage(LocalRepoLoaderImpl.java:305)
  at com.android.repository.impl.manager.LocalRepoLoaderImpl.parsePackages(LocalRepoLoaderImpl.java:159)
  at com.android.repository.impl.manager.LocalRepoLoaderImpl.getPackages(LocalRepoLoaderImpl.java:124)
  at com.android.repository.impl.manager.RepoManagerImpl${'$'}LoadTask.run(RepoManagerImpl.java:518)
  at com.android.repository.api.RepoManager${'$'}DummyProgressRunner.runSyncWithProgress(RepoManager.java:397)
  at com.android.repository.impl.manager.RepoManagerImpl.load(RepoManagerImpl.java:365)
  at com.android.repository.api.RepoManager.loadSynchronously(RepoManager.java:290)
  at com.android.sdklib.repository.AndroidSdkHandler${'$'}RepoConfig.createRepoManager(AndroidSdkHandler.java:731)
  at com.android.sdklib.repository.AndroidSdkHandler.getSdkManager(AndroidSdkHandler.java:297)
  at com.android.builder.sdk.DefaultSdkLoader.init(DefaultSdkLoader.java:393)
  at com.android.builder.sdk.DefaultSdkLoader.getSdkInfo(DefaultSdkLoader.java:374)
  at com.android.build.gradle.internal.SdkHandler.initTarget(SdkHandler.java:179)
  at com.android.build.gradle.BasePlugin.ensureTargetSetup(BasePlugin.java:980)
  at com.android.build.gradle.BasePlugin.createAndroidTasks(BasePlugin.java:752)
  at com.android.build.gradle.BasePlugin.lambda${'$'}null${'$'}4(BasePlugin.java:690)
  at com.android.builder.profile.ThreadRecorder.record(ThreadRecorder.java:81)
  at com.android.build.gradle.BasePlugin.lambda${'$'}createTasks${'$'}5(BasePlugin.java:686)
  at org.gradle.configuration.internal.DefaultListenerBuildOperationDecorator${'$'}BuildOperationEmittingAction${'$'}1${'$'}1.run(DefaultListenerBuildOperationDecorator.java:157)
  at org.gradle.configuration.internal.DefaultUserCodeApplicationContext.reapply(DefaultUserCodeApplicationContext.java:58)
  at org.gradle.configuration.internal.DefaultListenerBuildOperationDecorator${'$'}BuildOperationEmittingAction${'$'}1.run(DefaultListenerBuildOperationDecorator.java:154)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.configuration.internal.DefaultListenerBuildOperationDecorator${'$'}BuildOperationEmittingAction.execute(DefaultListenerBuildOperationDecorator.java:151)
  at org.gradle.internal.event.BroadcastDispatch${'$'}ActionInvocationHandler.dispatch(BroadcastDispatch.java:91)
  at org.gradle.internal.event.BroadcastDispatch${'$'}ActionInvocationHandler.dispatch(BroadcastDispatch.java:80)
  at org.gradle.internal.event.AbstractBroadcastDispatch.dispatch(AbstractBroadcastDispatch.java:42)
  at org.gradle.internal.event.BroadcastDispatch${'$'}SingletonDispatch.dispatch(BroadcastDispatch.java:230)
  at org.gradle.internal.event.BroadcastDispatch${'$'}SingletonDispatch.dispatch(BroadcastDispatch.java:149)
  at org.gradle.internal.event.AbstractBroadcastDispatch.dispatch(AbstractBroadcastDispatch.java:58)
  at org.gradle.internal.event.BroadcastDispatch${'$'}CompositeDispatch.dispatch(BroadcastDispatch.java:324)
  at org.gradle.internal.event.BroadcastDispatch${'$'}CompositeDispatch.dispatch(BroadcastDispatch.java:234)
  at org.gradle.internal.event.ListenerBroadcast.dispatch(ListenerBroadcast.java:140)
  at org.gradle.internal.event.ListenerBroadcast.dispatch(ListenerBroadcast.java:37)
  at org.gradle.internal.dispatch.ProxyDispatchAdapter${'$'}DispatchingInvocationHandler.invoke(ProxyDispatchAdapter.java:93)
  at com.sun.proxy.${'$'}Proxy35.afterEvaluate(Unknown Source)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}NotifyAfterEvaluate${'$'}1.execute(LifecycleProjectEvaluator.java:191)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}NotifyAfterEvaluate${'$'}1.execute(LifecycleProjectEvaluator.java:188)
  at org.gradle.api.internal.project.DefaultProject.stepEvaluationListener(DefaultProject.java:1413)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}NotifyAfterEvaluate.run(LifecycleProjectEvaluator.java:197)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.internal.operations.DelegatingBuildOperationExecutor.run(DelegatingBuildOperationExecutor.java:31)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}EvaluateProject${'$'}1.run(LifecycleProjectEvaluator.java:112)
  at org.gradle.internal.Factories${'$'}1.create(Factories.java:25)
  at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:183)
  at org.gradle.internal.work.StopShieldingWorkerLeaseService.withLocks(StopShieldingWorkerLeaseService.java:40)
  at org.gradle.api.internal.project.DefaultProjectStateRegistry${'$'}ProjectStateImpl.withProjectLock(DefaultProjectStateRegistry.java:226)
  at org.gradle.api.internal.project.DefaultProjectStateRegistry${'$'}ProjectStateImpl.withMutableState(DefaultProjectStateRegistry.java:220)
  at org.gradle.api.internal.project.DefaultProjectStateRegistry${'$'}ProjectStateImpl.withMutableState(DefaultProjectStateRegistry.java:186)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}EvaluateProject.run(LifecycleProjectEvaluator.java:96)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.internal.operations.DelegatingBuildOperationExecutor.run(DelegatingBuildOperationExecutor.java:31)
  at org.gradle.configuration.project.LifecycleProjectEvaluator.evaluate(LifecycleProjectEvaluator.java:68)
  at org.gradle.api.internal.project.DefaultProject.evaluate(DefaultProject.java:687)
  at org.gradle.api.internal.project.DefaultProject.evaluate(DefaultProject.java:140)
  at org.gradle.execution.TaskPathProjectEvaluator.configure(TaskPathProjectEvaluator.java:35)
  at org.gradle.execution.TaskPathProjectEvaluator.configureHierarchy(TaskPathProjectEvaluator.java:62)
  at org.gradle.configuration.DefaultBuildConfigurer.configure(DefaultBuildConfigurer.java:41)
  at org.gradle.initialization.DefaultGradleLauncher${'$'}ConfigureBuild.run(DefaultGradleLauncher.java:286)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.internal.operations.DelegatingBuildOperationExecutor.run(DelegatingBuildOperationExecutor.java:31)
  at org.gradle.initialization.DefaultGradleLauncher.configureBuild(DefaultGradleLauncher.java:194)
  at org.gradle.initialization.DefaultGradleLauncher.doBuildStages(DefaultGradleLauncher.java:150)
  at org.gradle.initialization.DefaultGradleLauncher.executeTasks(DefaultGradleLauncher.java:133)
  at org.gradle.internal.invocation.GradleBuildController${'$'}1.execute(GradleBuildController.java:79)
  at org.gradle.internal.invocation.GradleBuildController${'$'}1.execute(GradleBuildController.java:76)
  at org.gradle.internal.invocation.GradleBuildController${'$'}3.create(GradleBuildController.java:103)
  at org.gradle.internal.invocation.GradleBuildController${'$'}3.create(GradleBuildController.java:96)
  at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:183)
  at org.gradle.internal.work.StopShieldingWorkerLeaseService.withLocks(StopShieldingWorkerLeaseService.java:40)
  at org.gradle.internal.invocation.GradleBuildController.doBuild(GradleBuildController.java:96)
  at org.gradle.internal.invocation.GradleBuildController.run(GradleBuildController.java:76)
  at org.gradle.tooling.internal.provider.runner.ClientProvidedPhasedActionRunner.run(ClientProvidedPhasedActionRunner.java:61)
  at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
  at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
  at org.gradle.tooling.internal.provider.ValidatingBuildActionRunner.run(ValidatingBuildActionRunner.java:32)
  at org.gradle.launcher.exec.RunAsBuildOperationBuildActionRunner${'$'}3.run(RunAsBuildOperationBuildActionRunner.java:49)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.internal.operations.DelegatingBuildOperationExecutor.run(DelegatingBuildOperationExecutor.java:31)
  at org.gradle.launcher.exec.RunAsBuildOperationBuildActionRunner.run(RunAsBuildOperationBuildActionRunner.java:44)
  at org.gradle.tooling.internal.provider.SubscribableBuildActionRunner.run(SubscribableBuildActionRunner.java:51)
  at org.gradle.launcher.exec.InProcessBuildActionExecuter${'$'}1.transform(InProcessBuildActionExecuter.java:47)
  at org.gradle.launcher.exec.InProcessBuildActionExecuter${'$'}1.transform(InProcessBuildActionExecuter.java:44)
  at org.gradle.composite.internal.DefaultRootBuildState.run(DefaultRootBuildState.java:79)
  at org.gradle.launcher.exec.InProcessBuildActionExecuter.execute(InProcessBuildActionExecuter.java:44)
  at org.gradle.launcher.exec.InProcessBuildActionExecuter.execute(InProcessBuildActionExecuter.java:30)
  at org.gradle.launcher.exec.BuildTreeScopeBuildActionExecuter.execute(BuildTreeScopeBuildActionExecuter.java:39)
  at org.gradle.launcher.exec.BuildTreeScopeBuildActionExecuter.execute(BuildTreeScopeBuildActionExecuter.java:25)
  at org.gradle.tooling.internal.provider.ContinuousBuildActionExecuter.execute(ContinuousBuildActionExecuter.java:78)
  at org.gradle.tooling.internal.provider.ContinuousBuildActionExecuter.execute(ContinuousBuildActionExecuter.java:52)
  at org.gradle.tooling.internal.provider.ServicesSetupBuildActionExecuter.execute(ServicesSetupBuildActionExecuter.java:62)
  at org.gradle.tooling.internal.provider.ServicesSetupBuildActionExecuter.execute(ServicesSetupBuildActionExecuter.java:34)
  at org.gradle.tooling.internal.provider.GradleThreadBuildActionExecuter.execute(GradleThreadBuildActionExecuter.java:36)
  at org.gradle.tooling.internal.provider.GradleThreadBuildActionExecuter.execute(GradleThreadBuildActionExecuter.java:25)
  at org.gradle.tooling.internal.provider.ParallelismConfigurationBuildActionExecuter.execute(ParallelismConfigurationBuildActionExecuter.java:43)
  at org.gradle.tooling.internal.provider.ParallelismConfigurationBuildActionExecuter.execute(ParallelismConfigurationBuildActionExecuter.java:29)
  at org.gradle.tooling.internal.provider.StartParamsValidatingActionExecuter.execute(StartParamsValidatingActionExecuter.java:59)
  at org.gradle.tooling.internal.provider.StartParamsValidatingActionExecuter.execute(StartParamsValidatingActionExecuter.java:31)
  at org.gradle.tooling.internal.provider.SessionFailureReportingActionExecuter.execute(SessionFailureReportingActionExecuter.java:59)
  at org.gradle.tooling.internal.provider.SessionFailureReportingActionExecuter.execute(SessionFailureReportingActionExecuter.java:44)
  at org.gradle.tooling.internal.provider.SetupLoggingActionExecuter.execute(SetupLoggingActionExecuter.java:46)
  at org.gradle.tooling.internal.provider.SetupLoggingActionExecuter.execute(SetupLoggingActionExecuter.java:30)
  at org.gradle.launcher.daemon.server.exec.ExecuteBuild.doBuild(ExecuteBuild.java:67)
  at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:36)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.WatchForDisconnection.execute(WatchForDisconnection.java:37)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.ResetDeprecationLogger.execute(ResetDeprecationLogger.java:26)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.RequestStopIfSingleUsedDaemon.execute(RequestStopIfSingleUsedDaemon.java:34)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.ForwardClientInput${'$'}2.call(ForwardClientInput.java:74)
  at org.gradle.launcher.daemon.server.exec.ForwardClientInput${'$'}2.call(ForwardClientInput.java:72)
  at org.gradle.util.Swapper.swap(Swapper.java:38)
  at org.gradle.launcher.daemon.server.exec.ForwardClientInput.execute(ForwardClientInput.java:72)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.LogAndCheckHealth.execute(LogAndCheckHealth.java:55)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.LogToClient.doBuild(LogToClient.java:62)
  at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:36)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.EstablishBuildEnvironment.doBuild(EstablishBuildEnvironment.java:81)
  at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:36)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.StartBuildOrRespondWithBusy${'$'}1.run(StartBuildOrRespondWithBusy.java:50)
  at org.gradle.launcher.daemon.server.DaemonStateCoordinator${'$'}1.run(DaemonStateCoordinator.java:295)
  at org.gradle.internal.concurrent.ExecutorPolicy${'$'}CatchAndRecordFailures.onExecute(ExecutorPolicy.java:63)
  at org.gradle.internal.concurrent.ManagedExecutorImpl${'$'}1.run(ManagedExecutorImpl.java:46)
  at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
  at java.base/java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(ThreadPoolExecutor.java:628)
  at org.gradle.internal.concurrent.ThreadFactoryImpl${'$'}ManagedThreadRunnable.run(ThreadFactoryImpl.java:55)
  at java.base/java.lang.Thread.run(Thread.java:829)

unexpected element (uri:"", local:"extension-level"). Expected elements are <{}codename>,<{}api-level>
unexpected element (uri:"", local:"base-extension"). Expected elements are <{}codename>,<{}api-level>
Mapping new ns http://schemas.android.com/repository/android/common/02 to old ns http://schemas.android.com/repository/android/common/01
Mapping new ns http://schemas.android.com/repository/android/generic/02 to old ns http://schemas.android.com/repository/android/generic/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/02 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/03 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/02 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/03 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/04 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/03 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/02 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
cvc-complex-type.2.4.a: Invalid content was found starting with element 'extension-level'. One of '{codename, tag}' is expected.:
org.xml.sax.SAXParseException; lineNumber: 141; columnNumber: 263; cvc-complex-type.2.4.a: Invalid content was found starting with element 'extension-level'. One of '{codename, tag}' is expected.
  at java.xml/com.sun.org.apache.xerces.internal.util.ErrorHandlerWrapper.createSAXParseException(ErrorHandlerWrapper.java:204)
  at java.xml/com.sun.org.apache.xerces.internal.util.ErrorHandlerWrapper.error(ErrorHandlerWrapper.java:135)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:396)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:327)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:284)
  at java.xml/com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator${'$'}XSIErrorReporter.reportError(XMLSchemaValidator.java:511)
  at java.xml/com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator.reportSchemaError(XMLSchemaValidator.java:3599)
  at java.xml/com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator.handleStartElement(XMLSchemaValidator.java:1971)
  at java.xml/com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator.startElement(XMLSchemaValidator.java:829)
  at java.xml/com.sun.org.apache.xerces.internal.jaxp.validation.ValidatorHandlerImpl.startElement(ValidatorHandlerImpl.java:570)
  at com.sun.xml.bind.v2.runtime.unmarshaller.ValidatingUnmarshaller.startElement(ValidatingUnmarshaller.java:101)
  at com.sun.xml.bind.v2.runtime.unmarshaller.SAXConnector.startElement(SAXConnector.java:168)
  at java.xml/org.xml.sax.helpers.XMLFilterImpl.startElement(XMLFilterImpl.java:551)
  at com.android.repository.impl.meta.SchemaModuleUtil${'$'}NamespaceFallbackFilter.startElement(SchemaModuleUtil.java:403)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.AbstractSAXParser.startElement(AbstractSAXParser.java:510)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl.scanStartElement(XMLNSDocumentScannerImpl.java:374)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl${'$'}FragmentContentDriver.next(XMLDocumentFragmentScannerImpl.java:2710)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl.next(XMLDocumentScannerImpl.java:605)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl.next(XMLNSDocumentScannerImpl.java:112)
  at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl.scanDocument(XMLDocumentFragmentScannerImpl.java:534)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.XML11Configuration.parse(XML11Configuration.java:888)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.XML11Configuration.parse(XML11Configuration.java:824)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.XMLParser.parse(XMLParser.java:141)
  at java.xml/com.sun.org.apache.xerces.internal.parsers.AbstractSAXParser.parse(AbstractSAXParser.java:1216)
  at java.xml/com.sun.org.apache.xerces.internal.jaxp.SAXParserImpl${'$'}JAXPSAXParser.parse(SAXParserImpl.java:635)
  at java.xml/org.xml.sax.helpers.XMLFilterImpl.parse(XMLFilterImpl.java:357)
  at com.sun.xml.bind.v2.runtime.unmarshaller.UnmarshallerImpl.unmarshal0(UnmarshallerImpl.java:258)
  at com.sun.xml.bind.v2.runtime.unmarshaller.UnmarshallerImpl.unmarshal(UnmarshallerImpl.java:229)
  at javax.xml.bind.helpers.AbstractUnmarshallerImpl.unmarshal(AbstractUnmarshallerImpl.java:155)
  at javax.xml.bind.helpers.AbstractUnmarshallerImpl.unmarshal(AbstractUnmarshallerImpl.java:138)
  at com.android.repository.impl.meta.SchemaModuleUtil.unmarshal(SchemaModuleUtil.java:196)
  at com.android.repository.impl.manager.LocalRepoLoaderImpl.parsePackage(LocalRepoLoaderImpl.java:305)
  at com.android.repository.impl.manager.LocalRepoLoaderImpl.parsePackages(LocalRepoLoaderImpl.java:159)
  at com.android.repository.impl.manager.LocalRepoLoaderImpl.getPackages(LocalRepoLoaderImpl.java:124)
  at com.android.repository.impl.manager.RepoManagerImpl${'$'}LoadTask.run(RepoManagerImpl.java:518)
  at com.android.repository.api.RepoManager${'$'}DummyProgressRunner.runSyncWithProgress(RepoManager.java:397)
  at com.android.repository.impl.manager.RepoManagerImpl.load(RepoManagerImpl.java:365)
  at com.android.repository.api.RepoManager.loadSynchronously(RepoManager.java:290)
  at com.android.sdklib.repository.AndroidSdkHandler${'$'}RepoConfig.createRepoManager(AndroidSdkHandler.java:731)
  at com.android.sdklib.repository.AndroidSdkHandler.getSdkManager(AndroidSdkHandler.java:297)
  at com.android.builder.sdk.DefaultSdkLoader.init(DefaultSdkLoader.java:393)
  at com.android.builder.sdk.DefaultSdkLoader.getSdkInfo(DefaultSdkLoader.java:374)
  at com.android.build.gradle.internal.SdkHandler.initTarget(SdkHandler.java:179)
  at com.android.build.gradle.BasePlugin.ensureTargetSetup(BasePlugin.java:980)
  at com.android.build.gradle.BasePlugin.createAndroidTasks(BasePlugin.java:752)
  at com.android.build.gradle.BasePlugin.lambda${'$'}null${'$'}4(BasePlugin.java:690)
  at com.android.builder.profile.ThreadRecorder.record(ThreadRecorder.java:81)
  at com.android.build.gradle.BasePlugin.lambda${'$'}createTasks${'$'}5(BasePlugin.java:686)
  at org.gradle.configuration.internal.DefaultListenerBuildOperationDecorator${'$'}BuildOperationEmittingAction${'$'}1${'$'}1.run(DefaultListenerBuildOperationDecorator.java:157)
  at org.gradle.configuration.internal.DefaultUserCodeApplicationContext.reapply(DefaultUserCodeApplicationContext.java:58)
  at org.gradle.configuration.internal.DefaultListenerBuildOperationDecorator${'$'}BuildOperationEmittingAction${'$'}1.run(DefaultListenerBuildOperationDecorator.java:154)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.configuration.internal.DefaultListenerBuildOperationDecorator${'$'}BuildOperationEmittingAction.execute(DefaultListenerBuildOperationDecorator.java:151)
  at org.gradle.internal.event.BroadcastDispatch${'$'}ActionInvocationHandler.dispatch(BroadcastDispatch.java:91)
  at org.gradle.internal.event.BroadcastDispatch${'$'}ActionInvocationHandler.dispatch(BroadcastDispatch.java:80)
  at org.gradle.internal.event.AbstractBroadcastDispatch.dispatch(AbstractBroadcastDispatch.java:42)
  at org.gradle.internal.event.BroadcastDispatch${'$'}SingletonDispatch.dispatch(BroadcastDispatch.java:230)
  at org.gradle.internal.event.BroadcastDispatch${'$'}SingletonDispatch.dispatch(BroadcastDispatch.java:149)
  at org.gradle.internal.event.AbstractBroadcastDispatch.dispatch(AbstractBroadcastDispatch.java:58)
  at org.gradle.internal.event.BroadcastDispatch${'$'}CompositeDispatch.dispatch(BroadcastDispatch.java:324)
  at org.gradle.internal.event.BroadcastDispatch${'$'}CompositeDispatch.dispatch(BroadcastDispatch.java:234)
  at org.gradle.internal.event.ListenerBroadcast.dispatch(ListenerBroadcast.java:140)
  at org.gradle.internal.event.ListenerBroadcast.dispatch(ListenerBroadcast.java:37)
  at org.gradle.internal.dispatch.ProxyDispatchAdapter${'$'}DispatchingInvocationHandler.invoke(ProxyDispatchAdapter.java:93)
  at com.sun.proxy.${'$'}Proxy35.afterEvaluate(Unknown Source)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}NotifyAfterEvaluate${'$'}1.execute(LifecycleProjectEvaluator.java:191)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}NotifyAfterEvaluate${'$'}1.execute(LifecycleProjectEvaluator.java:188)
  at org.gradle.api.internal.project.DefaultProject.stepEvaluationListener(DefaultProject.java:1413)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}NotifyAfterEvaluate.run(LifecycleProjectEvaluator.java:197)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.internal.operations.DelegatingBuildOperationExecutor.run(DelegatingBuildOperationExecutor.java:31)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}EvaluateProject${'$'}1.run(LifecycleProjectEvaluator.java:112)
  at org.gradle.internal.Factories${'$'}1.create(Factories.java:25)
  at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:183)
  at org.gradle.internal.work.StopShieldingWorkerLeaseService.withLocks(StopShieldingWorkerLeaseService.java:40)
  at org.gradle.api.internal.project.DefaultProjectStateRegistry${'$'}ProjectStateImpl.withProjectLock(DefaultProjectStateRegistry.java:226)
  at org.gradle.api.internal.project.DefaultProjectStateRegistry${'$'}ProjectStateImpl.withMutableState(DefaultProjectStateRegistry.java:220)
  at org.gradle.api.internal.project.DefaultProjectStateRegistry${'$'}ProjectStateImpl.withMutableState(DefaultProjectStateRegistry.java:186)
  at org.gradle.configuration.project.LifecycleProjectEvaluator${'$'}EvaluateProject.run(LifecycleProjectEvaluator.java:96)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.internal.operations.DelegatingBuildOperationExecutor.run(DelegatingBuildOperationExecutor.java:31)
  at org.gradle.configuration.project.LifecycleProjectEvaluator.evaluate(LifecycleProjectEvaluator.java:68)
  at org.gradle.api.internal.project.DefaultProject.evaluate(DefaultProject.java:687)
  at org.gradle.api.internal.project.DefaultProject.evaluate(DefaultProject.java:140)
  at org.gradle.execution.TaskPathProjectEvaluator.configure(TaskPathProjectEvaluator.java:35)
  at org.gradle.execution.TaskPathProjectEvaluator.configureHierarchy(TaskPathProjectEvaluator.java:62)
  at org.gradle.configuration.DefaultBuildConfigurer.configure(DefaultBuildConfigurer.java:41)
  at org.gradle.initialization.DefaultGradleLauncher${'$'}ConfigureBuild.run(DefaultGradleLauncher.java:286)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.internal.operations.DelegatingBuildOperationExecutor.run(DelegatingBuildOperationExecutor.java:31)
  at org.gradle.initialization.DefaultGradleLauncher.configureBuild(DefaultGradleLauncher.java:194)
  at org.gradle.initialization.DefaultGradleLauncher.doBuildStages(DefaultGradleLauncher.java:150)
  at org.gradle.initialization.DefaultGradleLauncher.executeTasks(DefaultGradleLauncher.java:133)
  at org.gradle.internal.invocation.GradleBuildController${'$'}1.execute(GradleBuildController.java:79)
  at org.gradle.internal.invocation.GradleBuildController${'$'}1.execute(GradleBuildController.java:76)
  at org.gradle.internal.invocation.GradleBuildController${'$'}3.create(GradleBuildController.java:103)
  at org.gradle.internal.invocation.GradleBuildController${'$'}3.create(GradleBuildController.java:96)
  at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:183)
  at org.gradle.internal.work.StopShieldingWorkerLeaseService.withLocks(StopShieldingWorkerLeaseService.java:40)
  at org.gradle.internal.invocation.GradleBuildController.doBuild(GradleBuildController.java:96)
  at org.gradle.internal.invocation.GradleBuildController.run(GradleBuildController.java:76)
  at org.gradle.tooling.internal.provider.runner.ClientProvidedPhasedActionRunner.run(ClientProvidedPhasedActionRunner.java:61)
  at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
  at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
  at org.gradle.tooling.internal.provider.ValidatingBuildActionRunner.run(ValidatingBuildActionRunner.java:32)
  at org.gradle.launcher.exec.RunAsBuildOperationBuildActionRunner${'$'}3.run(RunAsBuildOperationBuildActionRunner.java:49)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:301)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor${'$'}RunnableBuildOperationWorker.execute(DefaultBuildOperationExecutor.java:293)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.execute(DefaultBuildOperationExecutor.java:175)
  at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:91)
  at org.gradle.internal.operations.DelegatingBuildOperationExecutor.run(DelegatingBuildOperationExecutor.java:31)
  at org.gradle.launcher.exec.RunAsBuildOperationBuildActionRunner.run(RunAsBuildOperationBuildActionRunner.java:44)
  at org.gradle.tooling.internal.provider.SubscribableBuildActionRunner.run(SubscribableBuildActionRunner.java:51)
  at org.gradle.launcher.exec.InProcessBuildActionExecuter${'$'}1.transform(InProcessBuildActionExecuter.java:47)
  at org.gradle.launcher.exec.InProcessBuildActionExecuter${'$'}1.transform(InProcessBuildActionExecuter.java:44)
  at org.gradle.composite.internal.DefaultRootBuildState.run(DefaultRootBuildState.java:79)
  at org.gradle.launcher.exec.InProcessBuildActionExecuter.execute(InProcessBuildActionExecuter.java:44)
  at org.gradle.launcher.exec.InProcessBuildActionExecuter.execute(InProcessBuildActionExecuter.java:30)
  at org.gradle.launcher.exec.BuildTreeScopeBuildActionExecuter.execute(BuildTreeScopeBuildActionExecuter.java:39)
  at org.gradle.launcher.exec.BuildTreeScopeBuildActionExecuter.execute(BuildTreeScopeBuildActionExecuter.java:25)
  at org.gradle.tooling.internal.provider.ContinuousBuildActionExecuter.execute(ContinuousBuildActionExecuter.java:78)
  at org.gradle.tooling.internal.provider.ContinuousBuildActionExecuter.execute(ContinuousBuildActionExecuter.java:52)
  at org.gradle.tooling.internal.provider.ServicesSetupBuildActionExecuter.execute(ServicesSetupBuildActionExecuter.java:62)
  at org.gradle.tooling.internal.provider.ServicesSetupBuildActionExecuter.execute(ServicesSetupBuildActionExecuter.java:34)
  at org.gradle.tooling.internal.provider.GradleThreadBuildActionExecuter.execute(GradleThreadBuildActionExecuter.java:36)
  at org.gradle.tooling.internal.provider.GradleThreadBuildActionExecuter.execute(GradleThreadBuildActionExecuter.java:25)
  at org.gradle.tooling.internal.provider.ParallelismConfigurationBuildActionExecuter.execute(ParallelismConfigurationBuildActionExecuter.java:43)
  at org.gradle.tooling.internal.provider.ParallelismConfigurationBuildActionExecuter.execute(ParallelismConfigurationBuildActionExecuter.java:29)
  at org.gradle.tooling.internal.provider.StartParamsValidatingActionExecuter.execute(StartParamsValidatingActionExecuter.java:59)
  at org.gradle.tooling.internal.provider.StartParamsValidatingActionExecuter.execute(StartParamsValidatingActionExecuter.java:31)
  at org.gradle.tooling.internal.provider.SessionFailureReportingActionExecuter.execute(SessionFailureReportingActionExecuter.java:59)
  at org.gradle.tooling.internal.provider.SessionFailureReportingActionExecuter.execute(SessionFailureReportingActionExecuter.java:44)
  at org.gradle.tooling.internal.provider.SetupLoggingActionExecuter.execute(SetupLoggingActionExecuter.java:46)
  at org.gradle.tooling.internal.provider.SetupLoggingActionExecuter.execute(SetupLoggingActionExecuter.java:30)
  at org.gradle.launcher.daemon.server.exec.ExecuteBuild.doBuild(ExecuteBuild.java:67)
  at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:36)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.WatchForDisconnection.execute(WatchForDisconnection.java:37)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.ResetDeprecationLogger.execute(ResetDeprecationLogger.java:26)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.RequestStopIfSingleUsedDaemon.execute(RequestStopIfSingleUsedDaemon.java:34)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.ForwardClientInput${'$'}2.call(ForwardClientInput.java:74)
  at org.gradle.launcher.daemon.server.exec.ForwardClientInput${'$'}2.call(ForwardClientInput.java:72)
  at org.gradle.util.Swapper.swap(Swapper.java:38)
  at org.gradle.launcher.daemon.server.exec.ForwardClientInput.execute(ForwardClientInput.java:72)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.LogAndCheckHealth.execute(LogAndCheckHealth.java:55)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.LogToClient.doBuild(LogToClient.java:62)
  at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:36)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.EstablishBuildEnvironment.doBuild(EstablishBuildEnvironment.java:81)
  at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:36)
  at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:122)
  at org.gradle.launcher.daemon.server.exec.StartBuildOrRespondWithBusy${'$'}1.run(StartBuildOrRespondWithBusy.java:50)
  at org.gradle.launcher.daemon.server.DaemonStateCoordinator${'$'}1.run(DaemonStateCoordinator.java:295)
  at org.gradle.internal.concurrent.ExecutorPolicy${'$'}CatchAndRecordFailures.onExecute(ExecutorPolicy.java:63)
  at org.gradle.internal.concurrent.ManagedExecutorImpl${'$'}1.run(ManagedExecutorImpl.java:46)
  at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
  at java.base/java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(ThreadPoolExecutor.java:628)
  at org.gradle.internal.concurrent.ThreadFactoryImpl${'$'}ManagedThreadRunnable.run(ThreadFactoryImpl.java:55)
  at java.base/java.lang.Thread.run(Thread.java:829)

unexpected element (uri:"", local:"extension-level"). Expected elements are <{}vendor>,<{}codename>,<{}abi>,<{}api-level>,<{}tag>
Errors limit exceeded. To receive all errors set com.sun.xml.bind logger to FINEST level.
unexpected element (uri:"", local:"base-extension"). Expected elements are <{}vendor>,<{}codename>,<{}abi>,<{}api-level>,<{}tag>
Mapping new ns http://schemas.android.com/repository/android/common/02 to old ns http://schemas.android.com/repository/android/common/01
Mapping new ns http://schemas.android.com/repository/android/generic/02 to old ns http://schemas.android.com/repository/android/generic/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/02 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/addon2/03 to old ns http://schemas.android.com/sdk/android/repo/addon2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/02 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/repository2/03 to old ns http://schemas.android.com/sdk/android/repo/repository2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/03 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01
Mapping new ns http://schemas.android.com/sdk/android/repo/sys-img2/02 to old ns http://schemas.android.com/sdk/android/repo/sys-img2/01

Deprecated Gradle features were used in this build, making it incompatible with Gradle 6.0.
Use '--warning-mode all' to show the individual deprecation warnings.
See https://docs.gradle.org/5.0/userguide/command_line_interface.html#sec:command_line_warnings

BUILD SUCCESSFUL in 4s

"""
    val buildListener =
      GradleBuildInvokerImpl.Companion.createBuildTaskListenerForTests(
        project,
        myFileDocumentManager,
        myRequest,
        ""
      )
    buildListener.onStart(myTaskId, project.basePath)
    buildListener.onTaskOutput(myTaskId, output, true)
    buildListener.onFailure(myTaskId, RuntimeException("test"))
    buildListener.onEnd(myTaskId)

    allBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)

    // Check BuildOutputWindow messages
    assertThat(buildEvents.filterIsInstance<MessageEvent>().joinToString(separator = "\n") { "${it.kind}: ${it.message}" })
      .isEqualTo("""
        WARNING: cvc-complex-type.2.4.a: Invalid content was found starting with element 'extension-level'. One of '{layoutlib}' is expected.
        WARNING: cvc-complex-type.2.4.d: Invalid content was found starting with element 'extension-level'. No child element is expected at this point.
        WARNING: cvc-complex-type.2.4.a: Invalid content was found starting with element 'extension-level'. One of '{codename, tag}' is expected.
      """.trimIndent())

    // Check metrics
    val buildOutputWindowEvents = myTracker.usages.filter {
      it.studioEvent.hasBuildOutputWindowStats()
    }

    assertThat(buildOutputWindowEvents).hasSize(1)

    val messages = buildOutputWindowEvents.first().studioEvent.buildOutputWindowStats.buildErrorMessagesList
    assertThat(messages).isNotNull()
    // We don't report warnings
    assertThat(messages).isEmpty()
  }

  private fun getOutputWithAndroidGradlePluginErrors(): String {
    val path = tempDir.newPath("styles.xml")
    val absolutePath = StringUtil.escapeBackSlashes(path.toAbsolutePath().toString())
    val outputWithAndroidGradlePluginErrors = """
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
      16 actionable tasks: 15 executed, 1 up-to-date
    """
    return outputWithAndroidGradlePluginErrors;
 }
}
