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
package com.android.tools.idea.streaming

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.idea.streaming.device.DEVICE_CLIENT_KEY
import com.android.tools.idea.streaming.device.DEVICE_CONTROLLER_KEY
import com.android.tools.idea.streaming.device.DEVICE_VIEW_KEY
import com.android.tools.idea.streaming.device.DeviceView
import com.android.tools.idea.streaming.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.streaming.emulator.EMULATOR_VIEW_KEY
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.CTRL_DOWN_MASK
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.KeyEvent.VK_E

/**
 * Executes an action related to device streaming.
 */
fun executeDeviceAction(
  actionId: String, displayView: AbstractDisplayView, project: Project, place: String = ActionPlaces.TOOLBAR) {
  val action = ActionManager.getInstance().getAction(actionId)
  val event = createTestEvent(displayView, project, place)
  action.update(event)
  assertThat(event.presentation.isEnabledAndVisible).isTrue()
  action.actionPerformed(event)
}

fun updateAndGetActionPresentation(
  actionId: String, displayView: AbstractDisplayView, project: Project, place: String = ActionPlaces.KEYBOARD_SHORTCUT): Presentation {
  val action = ActionManager.getInstance().getAction(actionId)
  val event = createTestEvent(displayView, project, place)
  action.update(event)
  return event.presentation
}

private fun createTestEvent(displayView: AbstractDisplayView, project: Project, place: String): AnActionEvent {
  val inputEvent = KeyEvent(displayView, KEY_RELEASED, System.currentTimeMillis(), CTRL_DOWN_MASK, VK_E, CHAR_UNDEFINED)
  return AnActionEvent(inputEvent, TestDataContext(displayView, project), place, Presentation(), ActionManager.getInstance(), 0)
}

private class TestDataContext(private val displayView: AbstractDisplayView, private val project: Project) : DataContext {
  private val emulatorView = displayView as? EmulatorView
  private val deviceView = displayView as? DeviceView

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      EMULATOR_VIEW_KEY.name -> emulatorView
      EMULATOR_CONTROLLER_KEY.name -> emulatorView?.emulator
      DEVICE_VIEW_KEY.name -> deviceView
      DEVICE_CLIENT_KEY.name -> deviceView?.deviceClient
      DEVICE_CONTROLLER_KEY.name -> deviceView?.deviceController
      DISPLAY_VIEW_KEY.name -> displayView
      ZOOMABLE_KEY.name -> displayView
      SERIAL_NUMBER_KEY.name -> emulatorView?.deviceSerialNumber ?: deviceView?.deviceSerialNumber
      CommonDataKeys.PROJECT.name -> project
      else -> null
    }
  }
}
