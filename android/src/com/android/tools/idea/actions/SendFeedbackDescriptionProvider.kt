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
package com.android.tools.idea.actions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.application

interface SendFeedbackDescriptionProvider {
  companion object {
    val EP_NAME = ExtensionPointName.create<SendFeedbackDescriptionProvider>("com.android.tools.idea.sendFeedbackDescriptionProvider")

    @JvmStatic
    fun getProviders(): Collection<SendFeedbackDescriptionProvider> {
      val extensionArea = application.extensionArea
      if (!extensionArea.hasExtensionPoint(EP_NAME.name)) {
        return emptyList()
      }
      return extensionArea.getExtensionPoint(EP_NAME).extensionList
    }
  }

  fun getDescription(project: Project?): Collection<String>
}