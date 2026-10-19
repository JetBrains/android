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

import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Thumb
import org.jetbrains.android.util.AndroidBundle.message

class KmpPluginPlaceholderProvider : WizardPluginPromotionTemplateProvider() {
  override fun getTemplates(): List<PluginPromotionTemplate> =
    listOf(
      promotionTemplate(FormFactor.Mobile),
      promotionTemplate(FormFactor.Generic),
    )

  private fun promotionTemplate(section: FormFactor): PluginPromotionTemplate =
    object : PluginPromotionTemplate {
      override val name: String
        get() = message("android.wizard.project.promotion.kmp.title")

      override val pluginId: String
        get() = KMP_PLUGIN_ID

      override fun thumb() =
        Thumb { this@KmpPluginPlaceholderProvider.javaClass.getResource("/META-INF/kmp_required_logo.png") }

      override val formFactor: FormFactor
        get() = section
    }

  companion object {
    private const val KMP_PLUGIN_ID = "com.jetbrains.kmm"
  }
}
