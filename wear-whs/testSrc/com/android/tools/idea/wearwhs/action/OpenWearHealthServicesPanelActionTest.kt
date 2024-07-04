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
package com.android.tools.idea.wearwhs.action

import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.emulator.EMULATOR_VIEW_KEY
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorId
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import java.nio.file.Paths
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OpenWearHealthServicesPanelActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  @get:Rule val fakePopupRule = JBPopupRule()
  private lateinit var emulatorView: EmulatorView

  @Before
  fun setUp() {
    StudioFlags.WEAR_HEALTH_SERVICES_PANEL.override(true)
    emulatorView =
      EmulatorView(
        projectRule.testRootDisposable,
        EmulatorController(
          EmulatorId(
            0,
            null,
            null,
            "avdId",
            "avdFolder",
            Paths.get("avdPath"),
            0,
            0,
            emptyList(),
            "",
          ),
          projectRule.testRootDisposable,
        ),
        0,
        null,
        false,
      )
  }

  @Test
  fun `OpenWearHealthServicesPanelAction opens popup`() {
    val action = OpenWearHealthServicesPanelAction()

    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .add(EMULATOR_VIEW_KEY, emulatorView)
        .build()
    val actionEvent =
      AnActionEvent(null, dataContext, "", Presentation(), ActionManager.getInstance(), 0)

    action.actionPerformed(actionEvent)
    assertThat(fakePopupRule.fakePopupFactory.balloonCount).isEqualTo(1)
  }
}
