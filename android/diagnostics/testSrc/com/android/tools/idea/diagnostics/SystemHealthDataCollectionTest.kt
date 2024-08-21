/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.diagnostics

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.UsageTrackerWriter
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.SystemHealthEvent
import com.google.wireless.android.sdk.stats.SystemHealthEvent.DeadlockStatus
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals


class SystemHealthDataCollectionTest {

  private lateinit var systemHealthDataCollection: SystemHealthDataCollection
  private lateinit var oldWriter: UsageTrackerWriter
  private lateinit var testUsageTracker: TestUsageTracker

  private lateinit var scheduler: VirtualTimeScheduler

  companion object {
    @JvmField
    @ClassRule
    var appRule = ApplicationRule()
  }

  @Before
  fun setUp() {
    systemHealthDataCollection = SystemHealthDataCollection()
    scheduler = VirtualTimeScheduler()
    scheduler.advanceBy(1L)
    testUsageTracker = TestUsageTracker(scheduler)
    oldWriter = UsageTracker.setWriterForTest(testUsageTracker)
    systemHealthDataCollection.clock = SystemHealthDataCollection.Clock { scheduler.currentTimeMillis }
    systemHealthDataCollection.dedicatedThreadExecutor = scheduler
  }

  @After
  fun tearDown() {
    UsageTracker.setWriterForTest(oldWriter)
  }

  @Test
  fun testGcThresholdMet() {
    systemHealthDataCollection.triggers.gcThresholdMet()
    val event = expectSingleEventAndClear()
    assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, event.kind)
    assertEquals(SystemHealthEvent.SystemHealthEventType.MEMORY_LOW_MEMORY_WARNING, event.systemHealthEvent.eventType)
    assertEquals(SystemHealthEvent.LowMemoryWarningType.BEFORE_GC, event.systemHealthEvent.memory.lowMemoryWarningType)
  }

  @Test
  fun testGcThresholdMetAfterCollection() {
    systemHealthDataCollection.triggers.gcThresholdMetAfterCollection()
    val event = expectSingleEventAndClear()
    assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, event.kind)
    assertEquals(SystemHealthEvent.SystemHealthEventType.MEMORY_LOW_MEMORY_WARNING, event.systemHealthEvent.eventType)
    assertEquals(SystemHealthEvent.LowMemoryWarningType.AFTER_GC, event.systemHealthEvent.memory.lowMemoryWarningType)
  }

  @Test
  fun testOutOfMemoryErrorRaised() {
    systemHealthDataCollection.triggers.outOfMemoryErrorRaised()
    val event = expectSingleEventAndClear()
    assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, event.kind)
    assertEquals(SystemHealthEvent.SystemHealthEventType.MEMORY_OOM_ERROR, event.systemHealthEvent.eventType)
  }

  @Test
  fun testGracefulExitDetected() {
    systemHealthDataCollection.triggers.gracefulExitDetected("sessionid-1ab2c3")
    val event = expectSingleEventAndClear()
    assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, event.kind)
    assertEquals(SystemHealthEvent.SystemHealthEventType.EXIT_GRACEFUL, event.systemHealthEvent.eventType)
    assertEquals("sessionid-1ab2c3", event.systemHealthEvent.exit.studioSessionId)
  }

  @Test
  fun testNongracefulExitDetected() {
    systemHealthDataCollection.triggers.nongracefulExitDetected("sessionid-1ab2c3")
    val event = expectSingleEventAndClear()
    assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, event.kind)
    assertEquals(SystemHealthEvent.SystemHealthEventType.EXIT_NONGRACEFUL, event.systemHealthEvent.eventType)
    assertEquals("sessionid-1ab2c3", event.systemHealthEvent.exit.studioSessionId)
  }

  @Test
  fun testJvmCrashDetected() {
    systemHealthDataCollection.triggers.jvmCrashDetected("sessionid-1ab2c3", "SIGQUIT")
    val event = expectSingleEventAndClear()
    assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, event.kind)
    assertEquals(SystemHealthEvent.SystemHealthEventType.EXIT_JVM_CRASH, event.systemHealthEvent.eventType)
    assertEquals("sessionid-1ab2c3", event.systemHealthEvent.exit.studioSessionId)
    assertEquals(3, event.systemHealthEvent.exit.jvmSignalNumber)
  }

  @Test
  fun testInvalidSignal() {
    systemHealthDataCollection.triggers.jvmCrashDetected("sessionid-1ab2c3", "missing")
    val event = expectSingleEventAndClear()
    assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, event.kind)
    assertEquals(SystemHealthEvent.SystemHealthEventType.EXIT_JVM_CRASH, event.systemHealthEvent.eventType)
    assertEquals("sessionid-1ab2c3", event.systemHealthEvent.exit.studioSessionId)
    assertEquals(-1, event.systemHealthEvent.exit.jvmSignalNumber)
  }

  @Test
  fun testUnrecognizedSignal() {
    systemHealthDataCollection.triggers.jvmCrashDetected("sessionid-1ab2c3", "SIGNOVEL")
    val event = expectSingleEventAndClear()
    assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, event.kind)
    assertEquals(SystemHealthEvent.SystemHealthEventType.EXIT_JVM_CRASH, event.systemHealthEvent.eventType)
    assertEquals("sessionid-1ab2c3", event.systemHealthEvent.exit.studioSessionId)
    assertEquals(-2, event.systemHealthEvent.exit.jvmSignalNumber)
  }

  private fun expectSingleEventAndClear(): AndroidStudioEvent {
    val usages = testUsageTracker.usages
    UsefulTestCase.assertSize(1, usages)
    val studioEvent = usages[0].studioEvent
    usages.clear()
    return studioEvent
  }

  @Test
  fun testLongFreeze() {
    systemHealthDataCollection.triggers.uiFreezeStarted()
    scheduler.advanceBy(1L)
    val startedEvent = expectSingleEventAndClear()
    assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, startedEvent.kind)
    assertEquals(SystemHealthEvent.SystemHealthEventType.UI_FREEZE_STARTED, startedEvent.systemHealthEvent.eventType)
    assertEquals(1L, startedEvent.systemHealthEvent.uiFreeze.freezeId)
    assertEquals(5_000, startedEvent.systemHealthEvent.uiFreeze.durationMs)
    assertEquals(DeadlockStatus.UNKNOWN, startedEvent.systemHealthEvent.uiFreeze.deadlock)

    // Advance time by 8 hours
    for (i in 1..Duration.ofMinutes(10).toSeconds()) {
      scheduler.advanceBy(1, TimeUnit.SECONDS)
    }
    for (i in 1..Duration.ofMinutes(50).toMinutes()) {
      scheduler.advanceBy(1, TimeUnit.MINUTES)
    }
    for (i in 1..Duration.ofHours(7).toMinutes() / 5) {
      scheduler.advanceBy(5, TimeUnit.MINUTES)
    }

    val usages = testUsageTracker.usages
    assert(usages.all { it.studioEvent.systemHealthEvent.uiFreeze.freezeId == 1L })
    assert(usages.all { it.studioEvent.systemHealthEvent.eventType == SystemHealthEvent.SystemHealthEventType.UI_FREEZE_UPDATE })
    assertContentEquals(
      arrayOf(
        Duration.ofSeconds(10),
        Duration.ofSeconds(20),
        Duration.ofSeconds(30),
        Duration.ofSeconds(40),
        Duration.ofSeconds(50),
        Duration.ofMinutes(1),
        Duration.ofMinutes(2),
        Duration.ofMinutes(3),
        Duration.ofMinutes(4),
        Duration.ofMinutes(5),
        Duration.ofMinutes(10),
        Duration.ofMinutes(15),
        Duration.ofMinutes(20),
        Duration.ofMinutes(25),
        Duration.ofMinutes(30),
        Duration.ofMinutes(60),
        Duration.ofMinutes(90),
        Duration.ofMinutes(120),
        Duration.ofMinutes(150),
        Duration.ofMinutes(180),
        Duration.ofMinutes(210),
        Duration.ofMinutes(240),
        Duration.ofMinutes(270),
        Duration.ofMinutes(300),
        Duration.ofMinutes(330),
        Duration.ofMinutes(360)).map { it.toMillis() },
      usages.map { it.studioEvent.systemHealthEvent.uiFreeze.durationMs }.sorted()
    )
    usages.clear()

    systemHealthDataCollection.triggers.uiFreezeFinished(Duration.ofMinutes(120).toMillis())
    scheduler.advanceBy(1L)
    val finishedEvent = expectSingleEventAndClear()
    assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, finishedEvent.kind)
    assertEquals(SystemHealthEvent.SystemHealthEventType.UI_FREEZE_FINISHED, finishedEvent.systemHealthEvent.eventType)
    assertEquals(1L, finishedEvent.systemHealthEvent.uiFreeze.freezeId)
    assertEquals(Duration.ofMinutes(120).toMillis(), finishedEvent.systemHealthEvent.uiFreeze.durationMs)
    assertEquals(DeadlockStatus.NO_DEADLOCK, finishedEvent.systemHealthEvent.uiFreeze.deadlock)
  }

  @Test
  fun testManyFreezes() {
    for (i in 1..5) {
      systemHealthDataCollection.triggers.uiFreezeStarted()
      scheduler.advanceBy(1L)
      val startedEvent = expectSingleEventAndClear()
      assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, startedEvent.kind)
      assertEquals(SystemHealthEvent.SystemHealthEventType.UI_FREEZE_STARTED, startedEvent.systemHealthEvent.eventType)
      assertEquals(i.toLong(), startedEvent.systemHealthEvent.uiFreeze.freezeId)
      assertEquals(5_000, startedEvent.systemHealthEvent.uiFreeze.durationMs)
      scheduler.advanceBy(Duration.ofSeconds(5).toNanos())

      val updateEvent = expectSingleEventAndClear()
      assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, updateEvent.kind)
      assertEquals(SystemHealthEvent.SystemHealthEventType.UI_FREEZE_UPDATE, updateEvent.systemHealthEvent.eventType)
      assertEquals(i.toLong(), updateEvent.systemHealthEvent.uiFreeze.freezeId)
      assertEquals(10_000, updateEvent.systemHealthEvent.uiFreeze.durationMs)

      systemHealthDataCollection.triggers.uiFreezeFinished(Duration.ofSeconds(10).toMillis())
      scheduler.advanceBy(1L)
      val finishedEvent = expectSingleEventAndClear()
      assertEquals(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT, finishedEvent.kind)
      assertEquals(SystemHealthEvent.SystemHealthEventType.UI_FREEZE_FINISHED, finishedEvent.systemHealthEvent.eventType)
      assertEquals(i.toLong(), finishedEvent.systemHealthEvent.uiFreeze.freezeId)
      assertEquals(10_000, updateEvent.systemHealthEvent.uiFreeze.durationMs)
    }
  }
}
