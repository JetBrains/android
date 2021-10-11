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
package com.android.tools.idea.run.deployment

import com.android.tools.idea.run.LaunchCompatibility
import com.intellij.ide.HelpTooltip
import org.jetbrains.android.util.AndroidBundle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

internal class UpdatableDeviceHelpTooltip : HelpTooltip() {
  private var myCompatibility: LaunchCompatibility? = null

  init {
    createMouseListeners()
  }

  private fun createCustomMouseListener(): MouseAdapter {
    return object : MouseAdapter() {
      override fun mouseEntered(event: MouseEvent) {
        if (myCompatibility == null || myCompatibility!!.state == LaunchCompatibility.State.OK) {
          return
        }
        myMouseListener.mouseEntered(event)
      }

      override fun mouseExited(event: MouseEvent) = myMouseListener.mouseExited(event)

      override fun mouseMoved(event: MouseEvent) {
        if (myCompatibility == null || myCompatibility!!.state == LaunchCompatibility.State.OK) {
          return
        }
        myMouseListener.mouseMoved(event)
      }
    }
  }

  override fun installOn(component: JComponent) {
    val listener = createCustomMouseListener()
    component.addMouseListener(listener)
    component.addMouseMotionListener(listener)
  }

  fun updateTooltip(device: Device) {
    val compatibility = device.launchCompatibility
    if (compatibility == myCompatibility) {
      return
    }
    myCompatibility = compatibility
    hidePopup(true)
    updateTooltip(device, this)
    initPopupBuilder(this)
  }

  fun cancel() {
    myCompatibility = null
    hidePopup(true)
  }
}

internal fun updateTooltip(device: Device, helpTooltip: HelpTooltip): Boolean {
  val title = when (device.launchCompatibility.state) {
    LaunchCompatibility.State.OK -> return false
    LaunchCompatibility.State.WARNING -> AndroidBundle.message("warning.level.title")
    LaunchCompatibility.State.ERROR -> AndroidBundle.message("error.level.title")
  }

  helpTooltip.setTitle(title)
  helpTooltip.setDescription(device.launchCompatibility.reason)

  return true
}
