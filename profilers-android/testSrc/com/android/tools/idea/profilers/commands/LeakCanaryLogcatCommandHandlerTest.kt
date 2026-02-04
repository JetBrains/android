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
package com.android.tools.idea.profilers.commands

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.testutils.TestUtils
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogcatHeader
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.service.LogcatService
import com.android.tools.idea.profilers.commands.util.FakeLogcatService
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.LeakCanary
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.google.common.collect.ImmutableList
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProjectEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.DisposableRule
import java.time.Instant
import java.util.concurrent.BlockingDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`

class LeakCanaryLogcatCommandHandlerTest {
  private lateinit var mockDevice: IDevice
  private lateinit var mockEventQueue: BlockingDeque<Common.Event>
  private lateinit var mockLogcatService: FakeLogcatService
  private lateinit var handler: LeakCanaryLogcatCommandHandler

  private val timer = FakeTimer()

  // Needed for GetCurrentTime
  private val service = FakeTransportService(timer)

  @get:Rule var grpcChannel = FakeGrpcChannel("LeakCanaryLogcatCommandHandlerTest", service)
  private val channel: ManagedChannel = InProcessChannelBuilder.forName(grpcChannel.name).usePlaintext().directExecutor().build()
  private var transportServiceGrpc = TransportServiceGrpc.newBlockingStub(channel)
  private val startTime = System.nanoTime()

  // endTime should be greater than startTime
  private val endTime = System.nanoTime() + 10000000

  @get:Rule val disposableRule = DisposableRule()

  @Before
  fun setUp() {
    val app = spy(MockApplication(disposableRule.disposable))
    mockDevice = mock(IDevice::class.java)
    mockEventQueue = LinkedBlockingDeque()
    mockLogcatService = FakeLogcatService()
    mockProjectDevice(disposableRule.disposable, app)
    ApplicationManager.setApplication(app, disposableRule.disposable)
    transportServiceGrpc = spy(TransportServiceGrpc.newBlockingStub(channel))
    `when`(transportServiceGrpc.getCurrentTime(any())).thenReturn(Transport.TimeResponse.newBuilder().setTimestampNs(startTime).build())
    handler = LeakCanaryLogcatCommandHandler(mockDevice, transportServiceGrpc, mockEventQueue)
  }

  @Test
  fun testHandleLogcatTracking() {
    assertTrue(shouldHandleCommand(Commands.Command.CommandType.START_LEAKCANARY_TASK))
    assertTrue(shouldHandleCommand(Commands.Command.CommandType.STOP_LEAKCANARY_TASK))
  }

  @Test
  fun testShouldNotHandleOtherCommands() {
    assertFalse(shouldHandleCommand(Commands.Command.CommandType.BEGIN_SESSION))
  }

  @Test
  fun testExecuteStartLogcatTracking() {
    val command =
      Commands.Command.newBuilder()
        .setType(Commands.Command.CommandType.START_LEAKCANARY_TASK)
        .setPid(123)
        .build()
    val response = handler.execute(command)
    assertNotNull(response)
  }

  @Test
  fun testExecuteStopLogcatTracking() {
    val command =
      Commands.Command.newBuilder()
        .setType(Commands.Command.CommandType.STOP_LEAKCANARY_TASK)
        .build()
    val response = handler.execute(command)
    assertNotNull(response)
  }

  @Test
  fun testLeakCanaryLogWithDifferentTag() = runTest {
    handler = LeakCanaryLogcatCommandHandler(mockDevice, transportServiceGrpc, mockEventQueue)
    handler.execute(
      Commands.Command.newBuilder().setType(Commands.Command.CommandType.START_LEAKCANARY_TASK).setPid(123).build()
    )

    // Before pushing messages wait for logcat to setup
    waitForEvent(this)
    val message1 =
      LogcatMessage(LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanaryRandom", Instant.ofEpochMilli(1000)), "HEAP ANALYSIS RESULT")
    val message2 = LogcatMessage(LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanaryRandom", Instant.ofEpochMilli(1000)), "METADATA")
    val message3 =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanaryRandom", Instant.ofEpochMilli(1000)),
        "====================================",
      )

    mockLogcatService.logMessages(message1, message2, message3)
    // Simulate some delay to allow coroutines to process
    waitForEvent(this)
    // Start event should exist in event queue.
    assertEquals(mockEventQueue.size, 1)
    verifyStartEvent()
    verifyEndEvent()
  }

  @Test fun testLogcatWithMultipleLeaksOneLinePerLogEntry() = testLogcatWithMultipleLeaks(true)

  @Test fun testLogcatWithMultipleLeaksMultiLinePerLogEntry() = testLogcatWithMultipleLeaks(false)

  private fun testLogcatWithMultipleLeaks(oneLinePerLogEntry: Boolean) = runTest {
    handler = LeakCanaryLogcatCommandHandler(mockDevice, transportServiceGrpc, mockEventQueue)
    handler.execute(
      Commands.Command.newBuilder().setType(Commands.Command.CommandType.START_LEAKCANARY_TASK).setPid(123).build()
    )

    // Before pushing messages wait for logcat to setup
    waitForEvent(this)
    val listOfFiles =
      ImmutableList.of("SingleApplicationLeak.txt", "SingleApplicationLeakAnalyzeCmd.txt", "MultiApplicationLeak.txt", "NoLeak.txt")
    val fakedMessages = pushLogcatMessages(listOfFiles, mockLogcatService, oneLinePerLogEntry)

    // Simulate some delay to allow coroutines to process
    waitForEvent(this)
    // All leaks in logcat are detected and added to queue along with start event.
    deleteIncompleteLeaksFromQueue()
    assertEquals(mockEventQueue.size, 5)
    var index = 0
    verifyStartEvent()

    // Verify all logcat leakCanary messages
    while (!mockEventQueue.isEmpty()) {
      val event = mockEventQueue.poll()
      assertEquals(event.leakcanaryAnalysis.data.trim(), fakedMessages[index++].trim())
    }
    verifyEndEvent()
  }

  @Test
  fun testLogcatWithCompleteLeakAfterInCompleteLeak() = runTest {
    handler = LeakCanaryLogcatCommandHandler(mockDevice, transportServiceGrpc, mockEventQueue)
    handler.execute(
      Commands.Command.newBuilder().setType(Commands.Command.CommandType.START_LEAKCANARY_TASK).setPid(123).build()
    )

    // Before pushing messages wait for logcat to setup
    waitForEvent(this)
    val listOfFiles = ImmutableList.of("SingleApplicationLeak.txt")
    val messageStart =
      LogcatMessage(LogcatHeader(LogLevel.DEBUG, 123, 2, "app1", "", "LeakCanary", Instant.ofEpochMilli(1000)), "HEAP ANALYSIS RESULT")
    val messageRandomStrings =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 123, 2, "app1", "", "LeakCanary", Instant.ofEpochMilli(1000)),
        "adfwfdsfsdfsdf sfsdfdsfdsfsdfdsf sdfsdfsdfsdfsd sdfdsfsdfsdfsd sdfsdfsdfsdsdfsdf",
      )

    mockLogcatService.logMessages(messageStart, messageRandomStrings)
    val fakedMessages = pushLogcatMessages(listOfFiles, mockLogcatService, true)

    // Simulate some delay to allow coroutines to process
    waitForEvent(this)

    // Only complete leak is taken into consideration and incomplete leaks are eliminated
    deleteIncompleteLeaksFromQueue()
    assertEquals(mockEventQueue.size, 2)

    verifyStartEvent()
    var index = 0
    while (!mockEventQueue.isEmpty()) {
      val event = mockEventQueue.poll()
      // Confirm that the data from the file written to logcat is what was read and that incomplete message appended to logcat are be
      // ignored.
      assertEquals(event.leakcanaryAnalysis.data.trim(), fakedMessages[index++].trim())
    }
    verifyEndEvent()
  }

  @Test
  fun testLogcatWithIncompleteLeak() = runTest {
    handler = LeakCanaryLogcatCommandHandler(mockDevice, transportServiceGrpc, mockEventQueue)
    handler.execute(
      Commands.Command.newBuilder().setType(Commands.Command.CommandType.START_LEAKCANARY_TASK).setPid(123).build()
    )

    // Before pushing messages wait for logcat to setup
    waitForEvent(this)

    val listOfFiles = ImmutableList.of("IncompleteLeakWithoutBytesRetained.txt")
    pushLogcatMessages(listOfFiles, mockLogcatService, false)

    // Simulate some delay to allow coroutines to process
    waitForEvent(this)

    // All leaks in logcat are detected and added to queue along with start event.
    assertEquals(mockEventQueue.size, 2)

    verifyStartEvent()

    val event = mockEventQueue.poll()
    // 0 is assigned for incomplete trace without retained bytes info
    assertTrue(event.leakcanaryAnalysis.data.contains("0 bytes retained by leaking objects"))

    verifyEndEvent()
  }

  @Test
  fun testLogcatWithShellInjection() = runTest {
    handler = LeakCanaryLogcatCommandHandler(mockDevice, transportServiceGrpc, mockEventQueue)
    handler.execute(Commands.Command.newBuilder().setType(Commands.Command.CommandType.START_LEAKCANARY_TASK).setPid(123).build())

    // Before pushing messages wait for logcat to setup
    waitForEvent(this)

    val validLeak =
      """
      ====================================
      HEAP ANALYSIS RESULT
      ====================================
      1 APPLICATION LEAKS

      References underlined with "~~~" are likely causes.
      Learn more at https://squ.re/leaks.

      0 bytes retained by leaking objects
      Signature: hash
      ┬───
      │ GC Root: System class
      │
      ╰→ java.lang.Object instance
           Leaking: YES (ObjectWatcher was watching this)
      ====================================
      0 LIBRARY LEAKS

      A Library Leak is a leak caused by a known bug in 3rd party code that you do not have control over.
      See https://square.github.io/leakcanary/fundamentals-how-leakcanary-works/#4-categorizing-leaks
      ====================================
      0 UNREACHABLE OBJECTS

      An unreachable object is still in memory but LeakCanary could not find a strong reference path
      from GC roots.
      ====================================
      METADATA

      Please include this in bug reports and Stack Overflow questions.
      Analysis duration: 1000 ms
      Heap dump file path: -
      Heap dump timestamp: 0
      Heap dump duration: Unknown
      ====================================
      """
        .trimIndent()

    // PID is different (999 vs 123), process name is arbitrary ("pid-29941"), BUT tag is "LeakCanary:Manual".
    // This simulates the user doing `adb shell log -t LeakCanary:Manual ...`
    val message1 = LogcatMessage(LogcatHeader(LogLevel.DEBUG, 999, 2, "-", "-", "LeakCanary:Manual", Instant.ofEpochMilli(1000)), validLeak)

    mockLogcatService.logMessages(message1)
    // Simulate some delay to allow coroutines to process
    waitForEvent(this)
    // Start event should exist in event queue.
    assertEquals(2, mockEventQueue.size)
    verifyStartEvent()

    val event = mockEventQueue.poll()
    // Verify the data was processed
    assertTrue(event.leakcanaryAnalysis.data.contains("HEAP ANALYSIS RESULT"))

    verifyEndEvent()
  }

  private fun verifyStartEvent() {
    val startEvent = mockEventQueue.poll()
    assertEquals(Common.Event.Kind.LEAKCANARY_ANALYSIS_STATUS, startEvent.kind)
    assertEquals(123, startEvent.groupId)
    assertEquals(startTime, startEvent.leakCanaryAnalysisStatus.analysisStarted.timestamp)
    assertEquals(0, startEvent.leakCanaryAnalysisStatus.analysisEnded.startTimestamp)
  }

  private fun verifyEndEvent() {
    `when`(transportServiceGrpc.getCurrentTime(any())).thenReturn(Transport.TimeResponse.newBuilder().setTimestampNs(endTime).build())
    handler.execute(
      Commands.Command.newBuilder().setType(Commands.Command.CommandType.STOP_LEAKCANARY_TASK).setPid(123).build()
    )
    assertEquals(mockEventQueue.size, 1)
    val leakInfoEndEvent = mockEventQueue.poll()
    assertEquals(Common.Event.Kind.LEAKCANARY_ANALYSIS_STATUS, leakInfoEndEvent.kind)
    assertEquals(123, leakInfoEndEvent.groupId)
    assertEquals(startTime, leakInfoEndEvent.leakCanaryAnalysisStatus.analysisEnded.startTimestamp)
    assertTrue(leakInfoEndEvent.isEnded)
    assertEquals(endTime, leakInfoEndEvent.leakCanaryAnalysisStatus.analysisEnded.endTimestamp)
    assertEquals(LeakCanary.LeakCanaryAnalysisEnded.Status.SUCCESS, leakInfoEndEvent.leakCanaryAnalysisStatus.analysisEnded.status)
  }

  private fun waitForEvent(testScope: TestScope) {
    testScope.advanceUntilIdle()
    val latch = CountDownLatch(1)
    testScope.launch {
      var allowedCount = 10
      while (mockEventQueue.isEmpty() && allowedCount > 0) {
        delay(10)
        allowedCount--
      }
      latch.countDown()
    }

    // Wait for the queue to process the messages
    latch.await(1, TimeUnit.SECONDS)
  }

  private fun shouldHandleCommand(commandType: Commands.Command.CommandType): Boolean {
    val command = Commands.Command.newBuilder().setType(commandType).build()
    return handler.shouldHandle(command)
  }

  private fun mockProjectDevice(disposable: Disposable, app: MockApplication) {
    val projectManagerMock = mock(ProjectManager::class.java)
    val projectMock = spy(MockProjectEx(disposable))
    projectMock.registerService(LogcatService::class.java, mockLogcatService)
    `when`(mockDevice.serialNumber).thenReturn("12345")
    `when`(mockDevice.version).thenReturn(AndroidVersion(26))
    `when`(app.getService(ProjectManager::class.java)).thenReturn(projectManagerMock)
    `when`(projectManagerMock.defaultProject).thenReturn(projectMock)
  }

  private suspend fun pushLogcatMessages(
    listOfFiles: ImmutableList<String>,
    mockLogcatService: FakeLogcatService,
    oneLinePerLogEntry: Boolean,
  ): MutableList<String> {
    val resultList = mutableListOf<String>()
    listOfFiles.forEach { fileName ->
      run {
        val file = TestUtils.resolveWorkspacePath("${TEST_DATA_PATH}/$fileName").toFile()
        val fileContent = file.readText()
        val fileContentEachLine = if (oneLinePerLogEntry) fileContent.split("\n") else listOf(fileContent)
        for (leakLine in fileContentEachLine) {
          val message = LogcatMessage(LogcatHeader(LogLevel.DEBUG, 123, 2, "app1", "", "LeakCanary", Instant.ofEpochMilli(1000)), leakLine)
          mockLogcatService.logMessages(message)
        }
        resultList.add(fileContent)
      }
    }
    return resultList
  }

  private fun deleteIncompleteLeaksFromQueue() {
    mockEventQueue.removeIf { event ->
      val analysisDurationString = event.leakcanaryAnalysis.data.split("\n").find { "Analysis duration" in it } ?: ""
      val isIncompleteTrace = analysisDurationString.split(":").last().trim() == "-1 ms"
      isIncompleteTrace
    }
  }

  companion object {
    private const val TEST_DATA_PATH = "tools/adt/idea/profilers/testData/sampleLeaks/"
  }
}
