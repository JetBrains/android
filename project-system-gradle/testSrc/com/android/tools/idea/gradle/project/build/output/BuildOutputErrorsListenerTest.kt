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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.project.build.output.BuildOutputErrorsListener
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.FilePosition
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
class BuildOutputErrorsListenerTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Mock
  private lateinit var myProject: Project
  private lateinit var buildId: ExternalSystemTaskId
  private val listenerDisposable = Disposer.newCheckedDisposable(BuildOutputErrorsListenerTest::class.java.name)
  private var collectedFailures: List<BuildErrorMessage>? = null
  private lateinit var buildOutputErrorsListener: BuildOutputErrorsListener
  private lateinit var messageEvent: MessageEvent

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    whenever(myProject.basePath).thenReturn("test")
    val moduleManager = Mockito.mock(ModuleManager::class.java)
    whenever(myProject.getService(ModuleManager::class.java)).thenReturn(moduleManager)
    whenever(moduleManager.modules).thenReturn(emptyArray<Module>())
    buildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myProject)
    buildOutputErrorsListener = BuildOutputErrorsListener(buildId, listenerDisposable) { collectedFailures = it }
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
  fun testMetricsReporting() {
    val folder = temporaryFolder.newFolder("test")
    messageEvent = FileMessageEventImpl(buildId, MessageEvent.Kind.ERROR, "Compiler", "error message", "error message",
                                        FilePosition(FileUtils.join(folder, "main", "src", "main.java"), 1, 2))
    buildOutputErrorsListener.onEvent(buildId, messageEvent)
    messageEvent = FileMessageEventImpl(buildId, MessageEvent.Kind.ERROR, "D8 errors", "error message", "error message",
                                        FilePosition(FileUtils.join(folder, "build", "intermediates", "res", "tmp"), -1, -1))
    buildOutputErrorsListener.onEvent(buildId, messageEvent)
    messageEvent = FileMessageEventImpl(buildId, MessageEvent.Kind.ERROR, "AAPT errors", "error message", "error message",
                                        FilePosition(FileUtils.join(folder, "build", "generated", "merged", "res", "merged.xml"), -1, -1))
    buildOutputErrorsListener.onEvent(buildId, messageEvent)
    messageEvent = MessageEventImpl(buildId, MessageEvent.Kind.ERROR, "Android Gradle Plugin errors", "error message", "error message")
    buildOutputErrorsListener.onEvent(buildId, messageEvent)
    messageEvent = MessageEventImpl(buildId, MessageEvent.Kind.ERROR, "Unknown error", "error message", "error message")
    buildOutputErrorsListener.onEvent(buildId, messageEvent)
    assertThat(listenerDisposable.isDisposed).isFalse()
    assertThat(collectedFailures).isNull()
    buildOutputErrorsListener.onEvent(buildId, FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), "failed", FailureResultImpl()))
    assertThat(listenerDisposable.isDisposed).isTrue()
    assertThat(collectedFailures).isNotNull()
    val messages = collectedFailures!!
    assertThat(messages).hasSize(5)
    checkSentMetricsData(messages[0], BuildErrorMessage.ErrorType.JAVA_COMPILER, BuildErrorMessage.FileType.PROJECT_FILE,
                         fileIncluded = true, lineIncluded = true)
    checkSentMetricsData(messages[1], BuildErrorMessage.ErrorType.D8, BuildErrorMessage.FileType.BUILD_GENERATED_FILE,
                         fileIncluded = true, lineIncluded = false)
    checkSentMetricsData(messages[2], BuildErrorMessage.ErrorType.AAPT, BuildErrorMessage.FileType.BUILD_GENERATED_FILE,
                         fileIncluded = true, lineIncluded = false)
    checkSentMetricsData(messages[3], BuildErrorMessage.ErrorType.GENERAL_ANDROID_GRADLE_PLUGIN,
                         BuildErrorMessage.FileType.UNKNOWN_FILE_TYPE,
                         fileIncluded = false, lineIncluded = false)
    checkSentMetricsData(messages[4], BuildErrorMessage.ErrorType.UNKNOWN_ERROR_TYPE, BuildErrorMessage.FileType.UNKNOWN_FILE_TYPE,
                         fileIncluded = false, lineIncluded = false)
  }

  @Test
  fun testNoErrorFoundMetricsReporting() {
    assertThat(listenerDisposable.isDisposed).isFalse()
    assertThat(collectedFailures).isNull()
    buildOutputErrorsListener.onEvent(buildId, FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), "failed", FailureResultImpl()))
    assertThat(listenerDisposable.isDisposed).isTrue()
    assertThat(collectedFailures).isEmpty()
  }

  @Test
  fun testNothingReportedOnSuccessfulBuild() {
    assertThat(listenerDisposable.isDisposed).isFalse()
    assertThat(collectedFailures).isNull()
    buildOutputErrorsListener.onEvent(buildId, FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), "finished", SuccessResultImpl()))
    assertThat(listenerDisposable.isDisposed).isTrue()
    assertThat(collectedFailures).isNull()
  }
}