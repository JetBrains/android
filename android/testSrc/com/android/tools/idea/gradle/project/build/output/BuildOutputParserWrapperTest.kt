/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.util.function.Consumer

class BuildOutputParserWrapperTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Mock
  private lateinit var myProject: Project

  private val tracker = TestUsageTracker(VirtualTimeScheduler())
  private val buildId = Mockito.mock(Object::class.java)

  private lateinit var myParserWrapper: BuildOutputParserWrapper
  private lateinit var messageEvent: MessageEvent

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    val parser = object : BuildOutputParser {
      override fun parse(line: String?, reader: BuildOutputInstantReader?, messageConsumer: Consumer<in BuildEvent>?): Boolean {
        messageConsumer?.accept(messageEvent)
        return true
      }
    }
    myParserWrapper = BuildOutputParserWrapper(parser)
    UsageTracker.setWriterForTest(tracker)

    `when`(myProject.basePath).thenReturn("test")

    val moduleManager = Mockito.mock(ModuleManager::class.java)
    `when`(myProject.getComponent(ModuleManager::class.java)).thenReturn(moduleManager)
    `when`(moduleManager.modules).thenReturn(emptyArray<Module>())
  }

  @After
  fun tearDown() {
    UsageTracker.cleanAfterTesting()
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
    messageEvent = FileMessageEventImpl(buildId, MessageEvent.Kind.ERROR, "Java compiler errors", "error message", "error message",
                                        FilePosition(FileUtils.join(folder, "main", "src", "main.java"), 1, 2))
    myParserWrapper.parse(null, null) {}

    messageEvent = FileMessageEventImpl(buildId, MessageEvent.Kind.ERROR, "D8 errors", "error message", "error message",
                                        FilePosition(FileUtils.join(folder, "build", "intermediates", "res", "tmp"), -1, -1))
    myParserWrapper.parse(null, null) {}


    messageEvent = FileMessageEventImpl(buildId, MessageEvent.Kind.ERROR, "AAPT errors", "error message", "error message",
                                        FilePosition(FileUtils.join(folder, "build", "generated", "merged", "res", "merged.xml"), -1, -1))
    myParserWrapper.parse(null, null) {}


    messageEvent = MessageEventImpl(buildId, MessageEvent.Kind.ERROR, "Android Gradle Plugin errors", "error message", "error message")
    myParserWrapper.parse(null, null) {}

    messageEvent = MessageEventImpl(buildId, MessageEvent.Kind.ERROR, "Unknown error", "error message", "error message")
    myParserWrapper.parse(null, null) {}

    sendBuildFailureMetrics(listOf(myParserWrapper), myProject)

    val buildOutputEvents = tracker.usages.filter{ use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_OUTPUT_WINDOW_STATS }
    assertThat(buildOutputEvents).hasSize(1)

    val buildOutputEvent = buildOutputEvents[0]
    val messages = buildOutputEvent.studioEvent.buildOutputWindowStats.buildErrorMessagesList
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
    sendBuildFailureMetrics(listOf(myParserWrapper), myProject)

    val buildOutputEvents = tracker.usages.filter{ use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_OUTPUT_WINDOW_STATS }
    assertThat(buildOutputEvents).hasSize(1)

    val buildOutputEvent = buildOutputEvents[0]
    val messages = buildOutputEvent.studioEvent.buildOutputWindowStats.buildErrorMessagesList
    assertThat(messages).hasSize(0)
  }
}