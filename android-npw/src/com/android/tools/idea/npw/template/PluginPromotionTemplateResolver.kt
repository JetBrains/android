/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.template

import com.intellij.openapi.extensions.ExtensionPointName

class PluginPromotionTemplateResolver {

  companion object {
    private val EP_NAME =
      ExtensionPointName<WizardPluginPromotionTemplateProvider>(
        "com.android.tools.idea.npw.template.wizardPluginPromotionTemplateProvider"
      )

    fun getAllPromotionTemplates(): List<PluginPromotionTemplate> =
      EP_NAME.extensionList.flatMap { it.getTemplates() }
  }
}
