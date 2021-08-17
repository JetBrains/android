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
package com.android.tools.adtui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JTable
import javax.swing.SwingUtilities

/**
 * Returns a Point where a popup could be placed from the [AnActionEvent], either the component from where the event was triggered or the
 * position of the mouse pointer.
 */
fun AnActionEvent.locationFromEvent(): Point {
  val source = this.componentFromEvent()
  if (source is Component) {
    val location = source.locationOnScreen
    return Point(location.x + source.width / 2, location.y + source.height / 2)
  }
  val input = this.inputEvent
  if (input is MouseEvent) {
    return input.locationOnScreen
  }
  return Point(20, 20)
}

/**
 * Returns the [Component] where is best to return focus when a popup is closed. Usually the [Component] from which the event was triggered.
 */
fun AnActionEvent.componentToRestoreFocusTo(): Component? {
  val component = this.componentFromEvent() ?: return null
  val table = SwingUtilities.getAncestorOfClass(JTable::class.java, component)
  return table ?: component
}

private fun AnActionEvent.componentFromEvent(): Component? {
  return PlatformDataKeys.CONTEXT_COMPONENT.getData(this.dataContext) ?: this.inputEvent?.component
}
