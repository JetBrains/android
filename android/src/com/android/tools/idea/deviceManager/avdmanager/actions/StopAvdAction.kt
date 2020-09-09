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
package com.android.tools.idea.deviceManager.avdmanager.actions

import com.android.tools.idea.deviceManager.avdmanager.AvdManagerConnection
import com.android.tools.idea.npw.invokeLater
import com.intellij.icons.AllIcons
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.beans.PropertyChangeListener
import javax.swing.Timer
import javax.swing.event.SwingPropertyChangeSupport

class StopAvdAction(
  provider: AvdInfoProvider
) : AvdUiAction(provider, "Stop", "Stop the emulator running this AVD", AllIcons.Actions.Suspend) {
  private var enabled: Boolean = false
  private val propertyChangeSupport = SwingPropertyChangeSupport(this)

  init {
    // TODO(qumeric): think about a better way to handle it. As minimum, stop it at some point.
    val delay = 1000 //milliseconds
    val taskPerformer = ActionListener {
      invokeLater {
        isEnabled = isAvdRunning(avdInfoProvider)
      }
    }
    Timer(delay, taskPerformer).start()
  }

  override fun setEnabled(enabled: Boolean) {
    val oldEnabled = this.enabled
    this.enabled = enabled
    propertyChangeSupport.firePropertyChange("enabled", oldEnabled, enabled)
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    propertyChangeSupport.addPropertyChangeListener(listener)
  }

  override fun isEnabled(): Boolean = enabled

  override fun actionPerformed(event: ActionEvent) {
    AvdManagerConnection.getDefaultAvdManagerConnection().stopAvd(avdInfo!!)
  }
}

private fun isAvdRunning(provider: AvdUiAction.AvdInfoProvider): Boolean =
  AvdManagerConnection.getDefaultAvdManagerConnection().isAvdRunning(provider.avdInfo!!)
