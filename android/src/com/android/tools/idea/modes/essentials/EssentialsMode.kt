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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.EssentialHighlightingMode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.RegistryManager

@Service
class EssentialsMode : ProjectActivity {
  companion object {

    private val messenger = service<EssentialsModeMessenger>()
    private val essentialsModeLogger = logger<EssentialsMode>()
    @JvmStatic
    fun isEnabled(): Boolean {
      return RegistryManager.getInstance().`is`("ide.essentials.mode");
    }

    @JvmStatic
    fun setEnabled(value: Boolean) {
      val beforeSet = isEnabled()
      RegistryManager.getInstance().get("ide.essentials.mode").setValue(value)

      // defensive case if EssentialHighlighting was enabled without the flag being enabled (e.g. prior registry value)
      if (!value || StudioFlags.ESSENTIALS_HIGHLIGHTING_MODE.get()) EssentialHighlightingMode.setEnabled(value)

      // send message if the value changed
      if (beforeSet != value) {
        messenger.sendMessage()
        essentialsModeLogger.info("Essentials mode isEnabled set to $value")
      }
    }
  }
  override suspend fun execute(project: Project) {
    essentialsModeLogger.info("Essentials mode isEnabled on start-up: ${isEnabled()}")
  }
}
