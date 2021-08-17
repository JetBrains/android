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
package com.android.tools.idea.emulator

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * Tests for [RunningEmulatorCatalog].
 */
class RunningEmulatorCatalogTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(emulatorRule)

  @Test
  fun testCatalogUpdates() {
    val catalog = RunningEmulatorCatalog.getInstance()
    val tempFolder = emulatorRule.root
    val emulator1 = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), standalone = false)
    val emulator2 = emulatorRule.newEmulator(FakeEmulator.createWatchAvd(tempFolder), standalone = false)
    val emulator3 = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), standalone = true)

    val eventQueue = LinkedBlockingDeque<CatalogEvent>()
    catalog.addListener(object : RunningEmulatorCatalog.Listener {
      override fun emulatorAdded(emulator: EmulatorController) {
        eventQueue.add(CatalogEvent(EventType.ADDED, emulator))
      }

      override fun emulatorRemoved(emulator: EmulatorController) {
        eventQueue.add(CatalogEvent(EventType.REMOVED, emulator))
      }
    }, updateIntervalMillis = 1000)

    // Check that the catalog is empty.
    assertThat(catalog.emulators).isEmpty()

    // Start the first Emulator and check that it is reflected in the catalog after an explicit update.
    emulator1.start()
    val emulators = catalog.updateNow().get()
    val event1: CatalogEvent = eventQueue.poll(50, TimeUnit.MILLISECONDS) ?: throw AssertionError("Listener was not called")
    assertThat(event1.type).isEqualTo(EventType.ADDED)
    assertThat(event1.emulator.emulatorId.grpcPort).isEqualTo(emulator1.grpcPort)
    assertThat(event1.emulator.emulatorId.avdFolder).isEqualTo(emulator1.avdFolder)
    assertThat(catalog.emulators).containsExactly(event1.emulator)
    assertThat(emulators).isEqualTo(catalog.emulators)

    // Start the second Emulator and check that a listener gets notified.
    emulator2.start()
    val event2 = eventQueue.poll(1500, TimeUnit.MILLISECONDS) ?: throw AssertionError("Listener was not called")
    assertThat(event2.type).isEqualTo(EventType.ADDED)
    assertThat(event2.emulator.emulatorId.grpcPort).isEqualTo(emulator2.grpcPort)
    assertThat(event2.emulator.emulatorId.avdFolder).isEqualTo(emulator2.avdFolder)
    assertThat(catalog.emulators).containsExactly(event1.emulator, event2.emulator)

    // Stop the first Emulator and check that a listener gets notified.
    emulator1.stop()
    val event3 = eventQueue.poll(1500, TimeUnit.MILLISECONDS) ?: throw AssertionError("Listener was not called")
    assertThat(event3.type).isEqualTo(EventType.REMOVED)
    assertThat(event3.emulator).isEqualTo(event1.emulator)
    assertThat(catalog.emulators).containsExactly(event2.emulator)

    // Stop the second Emulator and check that a listener gets notified.
    emulator2.stop()
    val event4 = eventQueue.poll(1500, TimeUnit.MILLISECONDS) ?: throw AssertionError("Listener was not called")
    assertThat(event4.type).isEqualTo(EventType.REMOVED)
    assertThat(event4.emulator).isEqualTo(event2.emulator)
    assertThat(catalog.emulators).isEmpty()

    // Start a standalone emulator and check that it is reflected in the catalog after an explicit update.
    emulator3.start()
    val event5: CatalogEvent = eventQueue.poll(1500, TimeUnit.MILLISECONDS) ?: throw AssertionError("Listener was not called")
    assertThat(event5.type).isEqualTo(EventType.ADDED)
    assertThat(catalog.emulators).containsExactly(event5.emulator)
    assertThat(event5.emulator.emulatorId.isEmbedded).isFalse()

    emulator3.stop()
    val event6 = eventQueue.poll(1500, TimeUnit.MILLISECONDS) ?: throw AssertionError("Listener was not called")
    assertThat(event6.type).isEqualTo(EventType.REMOVED)
    assertThat(event6.emulator).isEqualTo(event5.emulator)
    assertThat(catalog.updateNow().get()).isEmpty()
  }

  private enum class EventType { ADDED, REMOVED }

  private class CatalogEvent(val type: EventType, val emulator: EmulatorController)
}