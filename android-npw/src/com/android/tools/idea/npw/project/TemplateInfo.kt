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
package com.android.tools.idea.npw.project

import com.android.tools.idea.npw.template.PluginPromotionTemplate
import com.android.tools.idea.npw.toWizardFormFactor
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.Thumb
import com.android.tools.idea.wizard.template.WizardUiContext
import org.jetbrains.android.util.AndroidBundle.message

/**
 * A single clickable item in the new-project wizard's template grid.
 *
 * Wraps either a regular [Template] (creates a project) or a [PluginPromotionTemplate]
 * (offers to install a plugin instead of creating a project).
 */
sealed interface TemplateInfo {
  val name: String
  val formFactor: FormFactor
  fun thumb(): Thumb
}

data class NewProjectTemplateInfo(val template: Template) : TemplateInfo {
  override val name: String
    get() = template.name

  override val formFactor: FormFactor
    get() = template.formFactor

  val uiContexts: Collection<WizardUiContext>
    get() = template.uiContexts

  override fun thumb(): Thumb = template.thumb()
}

data class PluginPromotionTemplateInfo(private val template: PluginPromotionTemplate) : TemplateInfo {
  override val name: String
    get() = message("android.wizard.project.plugin.promotion.template.name", template.name)

  override val formFactor: FormFactor
    get() = template.formFactor

  val pluginId: String
    get() = template.pluginId

  override fun thumb(): Thumb = template.thumb()
}

fun TemplateInfo.getTemplateTitle(): String =
  when (this) {
    is NewProjectTemplateInfo ->
      name.replace("${template.formFactor.toWizardFormFactor().displayName} ", "")

    is PluginPromotionTemplateInfo ->
      name
  }
