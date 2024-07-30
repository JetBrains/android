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
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogcatHeader
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.service.LogcatService
import com.android.tools.idea.profilers.commands.util.FakeLogcatService
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.collect.ImmutableList
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProjectEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.DisposableRule
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.concurrent.BlockingDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class LeakCanaryLogcatCommandHandlerTest {
  private lateinit var mockDevice: IDevice
  private lateinit var mockEventQueue: BlockingDeque<Common.Event>
  private lateinit var mockLogcatService: FakeLogcatService
  private lateinit var handler: LeakCanaryLogcatCommandHandler

  @get:Rule
  val disposableRule = DisposableRule()

  @Before
  fun setUp() {
    val app = spy(MockApplication(disposableRule.disposable))
    mockDevice = mock(IDevice::class.java)
    mockEventQueue = LinkedBlockingDeque()
    mockLogcatService = FakeLogcatService()
    mockProjectDevice(disposableRule.disposable, app)
    ApplicationManager.setApplication(app, disposableRule.disposable)
    handler = LeakCanaryLogcatCommandHandler(mockDevice, mockEventQueue)
  }

  @Test
  fun testHandleLogcatTracking() {
    assertTrue(shouldHandleCommand(Commands.Command.CommandType.START_LOGCAT_TRACKING))
    assertTrue(shouldHandleCommand(Commands.Command.CommandType.STOP_LOGCAT_TRACKING))
  }

  @Test
  fun testShouldNotHandleOtherCommands() {
    assertFalse(shouldHandleCommand(Commands.Command.CommandType.BEGIN_SESSION))
  }

  @Test
  fun testExecuteStartLogcatTracking() {
    val command = Commands.Command.newBuilder()
      .setType(Commands.Command.CommandType.START_LOGCAT_TRACKING)
      .setPid(123)
      .build()
    val response = handler.execute(command)
    assertNotNull(response)
  }

  @Test
  fun testExecuteStopLogcatTracking() {
    val command = Commands.Command.newBuilder()
      .setType(Commands.Command.CommandType.STOP_LOGCAT_TRACKING)
      .build()
    val response = handler.execute(command)
    assertNotNull(response)
  }

  @Test
  fun testLeakCanaryLogWithDifferentTag() = runTest {
    handler = LeakCanaryLogcatCommandHandler(mockDevice, mockEventQueue)
    handler.execute(Commands.Command.newBuilder().setType(Commands.Command.CommandType.START_LOGCAT_TRACKING).setPid(123).build())

    // Before pushing messages wait for logcat to setup
    waitForEvent(this)
    val message1 =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanaryRandom", Instant.ofEpochMilli(1000)),
        "HEAP ANALYSIS RESULT",
      )
    val message2 =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanaryRandom", Instant.ofEpochMilli(1000)),
        "METADATA",
      )
    val message3 =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanaryRandom", Instant.ofEpochMilli(1000)),
        "====================================",
      )
    mockLogcatService.logMessages(message1, message2, message3)
    // Simulate some delay to allow coroutines to process
    waitForEvent(this)
    assertEquals(mockEventQueue.size, 0)
  }

  @Test
  fun testMixOfLeakCanaryAndOtherLogs() = runTest {
    handler = LeakCanaryLogcatCommandHandler(mockDevice, mockEventQueue)
    handler.execute(Commands.Command.newBuilder().setType(Commands.Command.CommandType.START_LOGCAT_TRACKING).setPid(123).build())

    // Before pushing messages wait for logcat to setup properly
    waitForEvent(this)
    val message1 =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanary", Instant.ofEpochMilli(1000)),
        "HEAP ANALYSIS RESULT",
      )
    val message2 =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "rasdsdsds", Instant.ofEpochMilli(1000)),
        "METADATA",
      )
    val message3 =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "rasdsdsds", Instant.ofEpochMilli(1000)),
        "====================================",
      )
    val message4 =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanary", Instant.ofEpochMilli(1000)),
        "METADATA",
      )
    val message5 =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanary", Instant.ofEpochMilli(1000)),
        "====================================",
      )
    mockLogcatService.logMessages(message1, message2, message3, message4, message2, message3, message5)
    // Simulate some delay to allow coroutines to process
    waitForEvent(this)
    assertEquals(mockEventQueue.size, 1)
    // Only LeakCanary tagged logs is taken. Initial "===" exist even though 'Heap Analysis' is considered the start of leak log.
    assertEquals(mockEventQueue.first.leakcanaryLogcat.logcatMessage,
                 "====================================\nHEAP ANALYSIS RESULT\nMETADATA\n====================================\n")
  }

  @Test
  fun testRunAndLogcatDetection() = runTest {
    handler = LeakCanaryLogcatCommandHandler(mockDevice, mockEventQueue)
    handler.execute(Commands.Command.newBuilder().setType(Commands.Command.CommandType.START_LOGCAT_TRACKING).setPid(123).build())

    // Before pushing messages wait for logcat to setup
    waitForEvent(this)
    val message1 =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanary", Instant.ofEpochMilli(1000)),
        "HEAP ANALYSIS RESULT",
      )
    val message2 =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanary", Instant.ofEpochMilli(1000)),
        "METADATA",
      )
    val message3 =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanary", Instant.ofEpochMilli(1000)),
        "====================================",
      )
    mockLogcatService.logMessages(message1, message2, message3)
    // Simulate some delay to allow coroutines to process
    waitForEvent(this)
    assertEquals(mockEventQueue.size, 1)
    // LeakCanary tagged logs is taken
    assertEquals(mockEventQueue.first.leakcanaryLogcat.logcatMessage,
                 "====================================\nHEAP ANALYSIS RESULT\nMETADATA\n====================================\n")
  }

  @Test
  fun testLogcatWithMultipleLeaks() = runTest {
    handler = LeakCanaryLogcatCommandHandler(mockDevice, mockEventQueue)
    handler.execute(Commands.Command.newBuilder().setType(Commands.Command.CommandType.START_LOGCAT_TRACKING).setPid(123).build())

    // Before pushing messages wait for logcat to setup
    waitForEvent(this)
    val listOfFiles = ImmutableList.of(
      "SingleApplicationLeak.txt",
      "SingleApplicationLeakAnalyzeCmd.txt",
      "MultiApplicationLeak.txt",
      "NoLeak.txt"
    )
    val fakedMessages = pushLogcatMessages(listOfFiles, mockLogcatService)

    // Simulate some delay to allow coroutines to process
    waitForEvent(this)
    // All leaks in logcat are detected and added to queue
    assertEquals(mockEventQueue.size, 4)
    var index = 0
    // Verify all logcat leakCanary messages
    mockEventQueue.forEach { event ->
      assertEquals(event.leakcanaryLogcat.logcatMessage.trim(), fakedMessages[index++].trim())
    }
  }

  @Test
  fun testLogcatWithCompleteLeakAfterInCompleteLeak() = runTest {
    handler = LeakCanaryLogcatCommandHandler(mockDevice, mockEventQueue)
    handler.execute(Commands.Command.newBuilder().setType(Commands.Command.CommandType.START_LOGCAT_TRACKING).setPid(123).build())

    // Before pushing messages wait for logcat to setup
    waitForEvent(this)
    val listOfFiles = ImmutableList.of(
      "SingleApplicationLeak.txt"
    )
    val messageStart =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanary", Instant.ofEpochMilli(1000)),
        "HEAP ANALYSIS RESULT",
      )
    val messageRandomStrings =
      LogcatMessage(
        LogcatHeader(LogLevel.DEBUG, 1, 2, "app1", "", "LeakCanary", Instant.ofEpochMilli(1000)),
        "adfwfdsfsdfsdf sfsdfdsfdsfsdfdsf sdfsdfsdfsdfsd sdfdsfsdfsdfsd sdfsdfsdfsdsdfsdf",
      )

    mockLogcatService.logMessages(messageStart, messageRandomStrings)
    val fakedMessages = pushLogcatMessages(listOfFiles, mockLogcatService)

    // Simulate some delay to allow coroutines to process
    waitForEvent(this)

    // Only complete leak is taken into consideration and incomplete leaks are eliminated
    assertEquals(mockEventQueue.size, 1)

    var index = 0
    mockEventQueue.forEach { event ->
      assertEquals(event.leakcanaryLogcat.logcatMessage.trim(), fakedMessages[index++].trim())
    }
  }

  private fun waitForEvent(testScope: TestScope) {
    testScope.advanceUntilIdle()
    val latch = CountDownLatch(1)
    testScope.launch {
      var allowedCount = 10;
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
    val command = Commands.Command.newBuilder()
      .setType(commandType)
      .build()
    return handler.shouldHandle(command)
  }

  private fun mockProjectDevice(disposable: Disposable ,app: MockApplication) {
    val projectManagerMock = mock(ProjectManager::class.java)
    val projectMock = spy(MockProjectEx(disposable))
    projectMock.registerService(LogcatService::class.java, mockLogcatService)
    `when`(mockDevice.serialNumber).thenReturn("12345")
    `when`(mockDevice.version).thenReturn(AndroidVersion(26))
    `when`(app.getService(ProjectManager::class.java)).thenReturn(projectManagerMock)
    `when`(projectManagerMock.defaultProject).thenReturn(projectMock)
  }

  private suspend fun pushLogcatMessages(listOfFiles: ImmutableList<String>, mockLogcatService: FakeLogcatService): MutableList<String> {
    val resultList = mutableListOf<String>()
    listOfFiles.forEach { fileName ->
      run {
        val file = TestUtils.resolveWorkspacePath("${TEST_DATA_PATH}/$fileName").toFile()
        val fileContent = file.readText()
        val fileContentEachLine = fileContent.split("\n")
        for (leakLine in fileContentEachLine) {
          val message =
            LogcatMessage(
              LogcatHeader(LogLevel.DEBUG, 1, 2, "app1",
                           "", "LeakCanary", Instant.ofEpochMilli(1000)),
              leakLine,
            )
          mockLogcatService.logMessages(message)
        }
        resultList.add(fileContent)
      }
    }
    return resultList
  }

  companion object {
    private const val TEST_DATA_PATH = "tools/adt/idea/profilers/testData/sampleLeaks/"
  }
}