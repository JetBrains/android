/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** [MouseAdapter] dispatches all events to a [target]. */
class DispatchToTargetAdapter(private val target: JComponent) : MouseAdapter() {
  override fun mouseMoved(e: MouseEvent?) {
    dispatchToTarget(e)
  }

  override fun mouseClicked(e: MouseEvent?) {
    dispatchToTarget(e)
  }

  override fun mouseDragged(e: MouseEvent?) {
    dispatchToTarget(e)
  }

  override fun mouseEntered(e: MouseEvent?) {
    dispatchToTarget(e)
  }

  override fun mouseExited(e: MouseEvent?) {
    dispatchToTarget(e)
  }

  override fun mousePressed(e: MouseEvent?) {
    dispatchToTarget(e)
  }

  override fun mouseReleased(e: MouseEvent?) {
    dispatchToTarget(e)
  }

  override fun mouseWheelMoved(e: MouseWheelEvent?) {
    dispatchToTarget(e)
  }

  private fun dispatchToTarget(e: MouseEvent?) {
    e ?: return
    val targetEvent = SwingUtilities.convertMouseEvent(e.source as JComponent, e, target)
    target.dispatchEvent(targetEvent)
  }
}
