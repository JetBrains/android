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
package com.android.tools.idea.streaming.device.actions

import com.android.tools.idea.streaming.device.DEVICE_CONFIGURATION_KEY
import com.android.tools.idea.streaming.device.DEVICE_CONTROLLER_KEY
import com.android.tools.idea.streaming.device.DEVICE_VIEW_KEY
import com.android.tools.idea.streaming.device.DeviceConfiguration
import com.android.tools.idea.streaming.device.DeviceController
import com.android.tools.idea.streaming.device.DeviceView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import java.util.function.Predicate

/**
 * Common superclass for toolbar actions for mirrored physical devices.
 *
 * @param configFilter determines the types of devices the action is applicable to
 */
internal abstract class AbstractDeviceAction(private val configFilter: Predicate<DeviceConfiguration>? = null) : AnAction(), DumbAware {

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    presentation.isEnabled = isEnabled(event)
    if (configFilter != null) {
      presentation.isVisible = getDeviceConfig(event)?.let(configFilter::test) ?: false
    }
  }

  protected open fun isEnabled(event: AnActionEvent): Boolean =
    isDeviceConnected(event)
}

internal fun getProject(event: AnActionEvent): Project =
  event.getRequiredData(CommonDataKeys.PROJECT)

internal fun getDeviceController(event: AnActionEvent): DeviceController? =
  event.dataContext.getData(DEVICE_CONTROLLER_KEY)

internal fun getDeviceView(event: AnActionEvent): DeviceView? =
  event.dataContext.getData(DEVICE_VIEW_KEY)

internal fun getDeviceConfig(event: AnActionEvent): DeviceConfiguration? =
  event.dataContext.getData(DEVICE_CONFIGURATION_KEY)

internal fun isDeviceConnected(event: AnActionEvent) =
  getDeviceView(event)?.isConnected == true