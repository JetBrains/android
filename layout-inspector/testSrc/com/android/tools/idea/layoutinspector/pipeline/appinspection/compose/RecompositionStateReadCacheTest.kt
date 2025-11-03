/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.testutils.waitForCondition
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.COMPOSE3
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.pipeline.appinspection.FakeInspectorState
import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RuleChain
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Command
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.StateReadSettings
import org.junit.Rule
import org.junit.Test

private val PROCESS =
  MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

class RecompositionStateReadCacheTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val inspectionRule = AppInspectionInspectorRule(projectRule)
  private val inspectorRule =
    LayoutInspectorRule(listOf(inspectionRule.createInspectorClientProvider()), projectRule) {
      it.name == PROCESS.name
    }

  @get:Rule val rule = RuleChain(projectRule, inspectionRule, inspectorRule)

  @Test
  fun testSettingsUpdated() {
    inspectorRule.attachDevice(MODERN_DEVICE)
    val startFetchReceived = ReportingCountDownLatch(1)
    inspectionRule.viewInspector.listenWhen({ it.hasStartFetchCommand() }) { command ->
      assertThat(command.startFetchCommand.continuous).isTrue()
      startFetchReceived.countDown()
    }
    val inspectorState =
      FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createAllResponses()

    inspectorRule.processNotifier.fireConnected(PROCESS)
    startFetchReceived.await(10, TimeUnit.SECONDS)

    val model = inspectorRule.inspectorModel
    var lastCommand: Command? = null
    inspectionRule.composeInspector.listenWhen({ true }) { command -> lastCommand = command }

    model.stateReadsModel.observeAll()
    waitForCondition(10.seconds) {
      lastCommand?.specializedCase == Command.SpecializedCase.UPDATE_SETTINGS_COMMAND
    }
    assertThat(lastCommand!!.updateSettingsCommand.stateReadSettings)
      .isEqualTo(StateReadSettings.newBuilder().apply { allBuilder.maxStateReads = 5000 }.build())
    lastCommand = null

    model.stateReadsModel.observeNone()
    waitForCondition(10.seconds) {
      lastCommand?.specializedCase == Command.SpecializedCase.UPDATE_SETTINGS_COMMAND
    }
    assertThat(lastCommand!!.updateSettingsCommand.stateReadSettings)
      .isEqualTo(StateReadSettings.newBuilder().apply { noneBuilder }.build())
    lastCommand = null

    model.stateReadsModel.observeNode(model[COMPOSE2] as ComposeViewNode)
    model.stateReadsModel.observeNode(model[COMPOSE3] as ComposeViewNode)
    waitForCondition(10.seconds) {
      lastCommand?.specializedCase == Command.SpecializedCase.UPDATE_SETTINGS_COMMAND
    }
    assertThat(lastCommand!!.updateSettingsCommand.stateReadSettings.byId.composableToObserveList)
      .containsExactly(103, 104)
    assertThat(lastCommand!!.updateSettingsCommand.stateReadSettings.byId.maxStateReads)
      .isEqualTo(5000)
  }
}
