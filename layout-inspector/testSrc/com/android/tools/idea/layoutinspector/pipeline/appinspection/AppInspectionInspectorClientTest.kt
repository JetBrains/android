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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import layoutinspector.view.inspection.LayoutInspectorViewProtocol as ViewProtocol

val MODERN_PROCESS = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

class AppInspectionInspectorClientTest {
  private val inspectionRule = AppInspectionInspectorRule()
  private val inspectorRule = LayoutInspectorRule(inspectionRule.createInspectorClientProvider()) { listOf(MODERN_PROCESS.name) }

  @get:Rule
  val ruleChain = RuleChain.outerRule(inspectionRule).around(inspectorRule)

  @Test
  fun inspectorStartsFetchingOnConnect() = runBlocking {
    val startFetchReceived = CompletableDeferred<Unit>()
    inspectionRule.viewInspector.addCommandListener { command, response ->
      // For this test, we should only ever receive a "start fetch" command and no others.
      assertThat(command.specializedCase).isEqualTo(ViewProtocol.Command.SpecializedCase.START_FETCH_COMMAND)
      startFetchReceived.complete(Unit)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    startFetchReceived.await() // If here, we already successfully connected (and sent an initial command)
    assertThat(inspectorRule.inspectorClient).isInstanceOf(AppInspectionInspectorClient::class.java)
  }
}