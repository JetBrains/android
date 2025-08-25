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
package com.android.tools.idea.streaming.emulator

import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.testutils.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.executeStreamingAction
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.Project
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Allows tests to create [EmulatorView]s connected to [FakeEmulator]s.
 */
class EmulatorViewRule : TestRule {

  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  private val fakeEmulators = Int2ObjectOpenHashMap<FakeEmulator>()
  private val flagOverrides = object : ExternalResource() {
    override fun before() {
      StudioFlags.EMBEDDED_EMULATOR_SCREENSHOT_STATISTICS.override(true)
      StudioFlags.EMBEDDED_EMULATOR_TRACE_SCREENSHOTS.override(true)
      StudioFlags.EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS.override(true)
      StudioFlags.EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.override(true)
      StudioFlags.EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS.override(true)
    }

    override fun after() {
      StudioFlags.EMBEDDED_EMULATOR_SCREENSHOT_STATISTICS.clearOverride()
      StudioFlags.EMBEDDED_EMULATOR_TRACE_SCREENSHOTS.clearOverride()
      StudioFlags.EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS.clearOverride()
      StudioFlags.EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.clearOverride()
      StudioFlags.EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS.clearOverride()
    }
  }

  val disposable: Disposable
    get() = projectRule.testRootDisposable

  val project: Project
    get() = projectRule.project

  fun newEmulatorView(avdCreator: ((Path) -> Path)? = null): EmulatorView =
      newEmulatorDisplayPanel(avdCreator).displayView

  fun newEmulatorDisplayPanel(avdCreator: ((Path) -> Path)? = null): EmulatorDisplayPanel {
    val catalog = RunningEmulatorCatalog.getInstance()
    val tempFolder = emulatorRule.avdRoot
    val avdCreator = avdCreator ?: { path -> FakeEmulator.createPhoneAvd(path) }
    val fakeEmulator = emulatorRule.newEmulator(avdCreator(tempFolder))
    fakeEmulators[fakeEmulator.grpcPort] = fakeEmulator
    fakeEmulator.start()
    val emulators = runBlocking { catalog.updateNow().await() }
    val emulatorController = emulators.find { it.emulatorId.grpcPort == fakeEmulator.grpcPort }!!
    val displayPanel = EmulatorDisplayPanel(disposable, emulatorController, project, PRIMARY_DISPLAY_ID, null, false, true)
    waitForCondition(5.seconds) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    return displayPanel
  }

  fun executeAction(actionId: String, emulatorView: EmulatorView, place: String = ActionPlaces.TOOLBAR) {
    executeStreamingAction(actionId, emulatorView, projectRule.project, place)
  }

  fun getFakeEmulator(emulatorView: EmulatorView): FakeEmulator =
      fakeEmulators[emulatorView.emulator.emulatorId.grpcPort]

  override fun apply(base: Statement, description: Description): Statement =
      flagOverrides.apply(projectRule.apply(emulatorRule.apply(base, description), description), description)
}