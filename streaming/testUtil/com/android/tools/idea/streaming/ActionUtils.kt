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
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.core.ZoomablePanel
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
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.CTRL_DOWN_MASK
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.KeyEvent.VK_E

/** Executes an action related to device streaming. */
fun executeStreamingAction(actionId: String, source: Component, project: Project, place: String = ActionPlaces.TOOLBAR,
                           modifiers: Int = CTRL_DOWN_MASK,
                           extra: DataSnapshotProvider? = null) {
  val action = ActionManager.getInstance().getAction(actionId)
  executeStreamingAction(action, source, project, place = place, modifiers = modifiers, extra = extra)
}

/** Executes an action related to device streaming. */
fun executeStreamingAction(action: AnAction, source: Component, project: Project, place: String = ActionPlaces.TOOLBAR,
                           modifiers: Int = CTRL_DOWN_MASK,
                           extra: DataSnapshotProvider? = null) {
  val event = createTestEvent(source, project, place = place, modifiers = modifiers, extra = extra)
  action.update(event)
  assertThat(event.presentation.isEnabledAndVisible).isTrue()
  action.actionPerformed(event)
}

fun updateAndGetActionPresentation(actionId: String, source: Component, project: Project,
                                   place: String = ActionPlaces.KEYBOARD_SHORTCUT,
                                   extra: DataSnapshotProvider? = null): Presentation {
  val action = ActionManager.getInstance().getAction(actionId)
  return updateAndGetActionPresentation(action, source, project, place = place, extra = extra)
}

fun updateAndGetActionPresentation(action: AnAction, source: Component, project: Project,
                                   place: String = ActionPlaces.KEYBOARD_SHORTCUT,
                                   extra: DataSnapshotProvider? = null): Presentation {
  val event = createTestEvent(source, project, place, presentation = action.templatePresentation.clone(), extra = extra)
  action.update(event)
  return event.presentation
}

fun createTestEvent(source: Component, project: Project, place: String = ActionPlaces.KEYBOARD_SHORTCUT,
                    modifiers: Int = CTRL_DOWN_MASK, presentation: Presentation = Presentation(),
                    extra: DataSnapshotProvider? = null): AnActionEvent {
  val inputEvent = KeyEvent(source, KEY_RELEASED, System.currentTimeMillis(), modifiers, VK_E, CHAR_UNDEFINED)
  val dataContext = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT, TestDataSnapshotProvider(source, project, extra))
  return AnActionEvent(inputEvent, dataContext, place, presentation, ActionManager.getInstance(), 0)
}

private class TestDataSnapshotProvider(
  private val component: Component,
  private val project: Project,
  private val extra: DataSnapshotProvider?
) : DataSnapshotProvider {

  private val emulatorView
    get() = component as? EmulatorView
  private val deviceView
    get() = component as? DeviceView
  private val displayView
    get() = component as? AbstractDisplayView

  override fun dataSnapshot(sink: DataSink) {
    sink.apply {
      extra?.let { dataSnapshot(it) }
      set(EMULATOR_VIEW_KEY, emulatorView)
      set(EMULATOR_CONTROLLER_KEY, emulatorView?.emulator)
      set(DEVICE_VIEW_KEY, deviceView)
      set(DEVICE_CLIENT_KEY, deviceView?.deviceClient)
      set(DEVICE_CONTROLLER_KEY, deviceView?.deviceController)
      set(DISPLAY_VIEW_KEY, displayView)
      set(ZOOMABLE_KEY, component as? ZoomablePanel)
      set(SERIAL_NUMBER_KEY, displayView?.deviceSerialNumber)
      set(CommonDataKeys.PROJECT, project)
      set(PlatformCoreDataKeys.CONTEXT_COMPONENT, component)
    }
  }
}