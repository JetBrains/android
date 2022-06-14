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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorGotoDeclaration
import com.intellij.openapi.actionSystem.AnActionEvent
import org.junit.Test
import java.awt.event.KeyEvent

class GotoDeclarationStatisticsTest {

  @Test
  fun testBasics() {
    val goto = GotoDeclarationStatistics()
    goto.gotoSourceFromTreeActionMenu(mock())
    goto.gotoSourceFromTreeActionMenu(mockKeyShortcut())
    goto.gotoSourceFromTreeActionMenu(mockKeyShortcut())
    goto.gotoSourceFromDoubleClick()
    goto.gotoSourceFromDoubleClick()
    goto.gotoSourceFromDoubleClick()
    val data1 = DynamicLayoutInspectorGotoDeclaration.newBuilder()
    goto.save { data1 }
    assertThat(data1.clicksMenuAction).isEqualTo(1)
    assertThat(data1.keyStrokesShortcut).isEqualTo(2)
    assertThat(data1.doubleClicks).isEqualTo(3)

    val data2 = DynamicLayoutInspectorGotoDeclaration.newBuilder()
    goto.start()
    goto.save { data2 }
    assertThat(data2.clicksMenuAction).isEqualTo(0)
    assertThat(data2.keyStrokesShortcut).isEqualTo(0)
    assertThat(data2.doubleClicks).isEqualTo(0)
  }

  private fun mockKeyShortcut(): AnActionEvent {
    val event: AnActionEvent = mock()
    val input: KeyEvent = mock()
    whenever(event.inputEvent).thenReturn(input)
    return event
  }
}
