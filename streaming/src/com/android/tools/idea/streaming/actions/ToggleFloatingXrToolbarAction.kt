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
package com.android.tools.idea.streaming.actions

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.streaming.core.StreamingDevicePanel
import com.intellij.ide.ActivityTracker
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.util.messages.Topic
import java.util.EventListener

internal class ToggleFloatingXrToolbarAction : ToggleAction("Floating XR Navigation Controls"), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean =
    service<FloatingXrToolbarState>().floatingXrToolbarEnabled

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    service<FloatingXrToolbarState>().floatingXrToolbarEnabled = state
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    // Enabled only for XR devices.
    event.presentation.isEnabledAndVisible =
        event.toolWindowContents.find { it.isSelected && (it.component as? StreamingDevicePanel)?.deviceType == DeviceType.XR } != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

@Service
internal class FloatingXrToolbarState {

  private val appProperties = PropertiesComponent.getInstance()

  var floatingXrToolbarEnabled: Boolean
    get() = appProperties.getBoolean(FLOATING_XR_TOOLBAR_PROPERTY, FLOATING_XR_TOOLBAR_DEFAULT)
    set(value) {
      appProperties.setValue(FLOATING_XR_TOOLBAR_PROPERTY, value, FLOATING_XR_TOOLBAR_DEFAULT)
      ActivityTracker.getInstance().inc()
      ApplicationManager.getApplication().messageBus.syncPublisher(Listener.TOPIC).floatingXrToolbarStateChanged(value)
    }

  interface Listener : EventListener {
    companion object {
      val TOPIC = Topic<Listener>.create("Floating XR Toolbar state change", Listener::class.java)
    }

    fun floatingXrToolbarStateChanged(enabled: Boolean)
  }
}

private const val FLOATING_XR_TOOLBAR_PROPERTY = "com.android.tools.idea.streaming.floating.xr.toolbar"
private const val FLOATING_XR_TOOLBAR_DEFAULT = true
