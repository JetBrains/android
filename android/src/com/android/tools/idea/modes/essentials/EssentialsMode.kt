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
package com.android.tools.idea.modes.essentials

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.RegistryManager

object EssentialsMode : ProjectActivity {

  private val applicationService = ApplicationManager.getApplication().getService(
    EssentialsModeMessenger::class.java)
  private val essentialsModeLogger = logger<EssentialsMode>()
  override suspend fun execute(project: Project) {
    essentialsModeLogger.info("Essentials mode isEnabled on start-up: ${isEnabled()}")
  }

  @JvmStatic
  fun isEnabled(): Boolean {
    return RegistryManager.getInstance().`is`("ide.essentials.mode");
  }

  @JvmStatic
  fun setEnabled(value: Boolean) {
    val beforeSet = isEnabled()
    RegistryManager.getInstance().get("ide.essentials.mode").setValue(value)
    // send message if the value changed
    if (beforeSet != value) {
      applicationService.sendMessage()
      essentialsModeLogger.info("Essentials mode isEnabled set to $value")
    }
  }
}
