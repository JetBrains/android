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
package com.android.tools.idea.run.deployment

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.deployment.selector.DeviceAndSnapshotComboBoxAction
import com.android.tools.idea.run.deployment.selector.SelectMultipleDevicesAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer

/**
 * At startup, replace actions from the legacyselector package with actions from the selector
 * package if the new selector is enabled.
 */
class DeploymentTargetSelectorActionsInitializer : ActionConfigurationCustomizer {
  override fun customize(actionManager: ActionManager) {
    if (StudioFlags.DEPLOYMENT_TARGET_DEVICE_PROVISIONER_MIGRATION.get()) {
      replaceAction(actionManager, "DeviceAndSnapshotComboBox", DeviceAndSnapshotComboBoxAction())
      replaceAction(actionManager, "SelectMultipleDevices", SelectMultipleDevicesAction())
    }
  }

  private fun replaceAction(actionManager: ActionManager, actionId: String, newAction: AnAction) {
    val oldAction = actionManager.getAction(actionId)
    if (oldAction != null) {
      newAction.getTemplatePresentation().icon = oldAction.getTemplatePresentation().icon
      newAction.getTemplatePresentation().text = oldAction.getTemplatePresentation().text
      actionManager.replaceAction(actionId, newAction)
    } else {
      actionManager.registerAction(actionId, newAction)
    }
  }
}
