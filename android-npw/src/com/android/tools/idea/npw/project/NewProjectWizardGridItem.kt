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

import androidx.compose.runtime.Composable
import com.android.tools.idea.npw.template.PluginPromotionTemplate
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.Thumb
import com.android.tools.idea.wizard.template.WizardUiContext
import icons.StudioIllustrationsCompose
import org.jetbrains.android.util.AndroidBundle.message
import org.jetbrains.jewel.ui.component.Icon

/** A single clickable item in the new-project wizard's grid (supported across both legacy templates and the new template engine). */
interface GridItem {
  val title: String
  val description: String
    get() = ""

  @Composable fun Icon()
}

/** Legacy grid items for the classic project wizard template system. */
sealed interface LegacyGridItem : GridItem {
  val formFactor: FormFactor

  fun thumb(): Thumb

  @Composable
  override fun Icon() {
    GridItemImage(thumb())
  }
}

data class TemplateGridItem(val template: Template) : LegacyGridItem {
  override val title: String
    get() = template.name

  override val formFactor: FormFactor
    get() = template.formFactor

  val uiContexts: Collection<WizardUiContext>
    get() = template.uiContexts

  override fun thumb(): Thumb = template.thumb()

  @Composable
  override fun Icon() {
    if (template == Template.NoActivity) {
      Icon(key = StudioIllustrationsCompose.Wizards.NoActivity, contentDescription = "")
    } else {
      super.Icon()
    }
  }
}

data class PluginPromotionGridItem(val template: PluginPromotionTemplate) : LegacyGridItem {
  override val title
    get() = message("android.wizard.project.plugin.promotion.template.name", template.name)

  override val formFactor: FormFactor
    get() = template.formFactor

  val pluginId: String
    get() = template.pluginId

  override fun thumb(): Thumb = template.thumb()
}
