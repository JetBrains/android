/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.events.studiobot

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface StudioBotQuickFixProvider {
  fun isAvailable(): Boolean = false
  fun askGemini(context: GradleErrorContext, project: Project)

  companion object {
    val EP_NAME =
      ExtensionPointName.create<StudioBotQuickFixProvider>(
        "com.android.tools.idea.gradle.studioBotQuickFixProvider"
      )

    private val studioBotQuickFixProviderUnavailable =
      object : StudioBotQuickFixProvider {
        override fun askGemini(context: GradleErrorContext, project: Project) {}
      }

    fun getInstance(): StudioBotQuickFixProvider {
      return EP_NAME.extensionList.firstOrNull() ?: studioBotQuickFixProviderUnavailable
    }
  }
}