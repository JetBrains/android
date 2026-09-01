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
package com.android.tools.idea.npw.templateengine.api

import androidx.compose.runtime.Composable
import com.android.tools.adtui.device.FormFactor
import com.intellij.openapi.extensions.ExtensionPointName

/** Specification for rendering a promotional card in the Compose NPW gallery. */
data class PromotionCardSpec(
  val id: String,
  val title: String,
  val description: String = "",
  val formFactor: FormFactor = FormFactor.MOBILE,
  val subCategory: String? = null,
  val thumb: (@Composable () -> Unit)? = null,
  val badgeText: String? = null,
  val priority: Int = 0,
  val pluginId: String? = null,
  val templateName: String? = null,
  val onClickAction: (Map<String, Any>) -> Unit = {},
)

/** Specification for an external template contributed via plugin. */
data class ExternalTemplateSpec(
  val id: String,
  val title: String,
  val description: String,
  val formFactor: FormFactor = FormFactor.MOBILE,
  val thumb: (@Composable () -> Unit)? = null,
  val minSdk: Int = 21,
  val postProcessors: List<String> = emptyList(),
)

/** Extension point for plugins to contribute promotional cards or custom templates to New NPW (TemplateEngine). */
interface TemplateEngineProjectWizardContributor {
  companion object {
    val EP_NAME = ExtensionPointName.create<TemplateEngineProjectWizardContributor>("com.android.templateContributor")
  }

  /** Unique identifier of the contributing plugin. */
  val id: String

  /** Priority ordering for templates contributed by this contributor (higher = top). */
  val priority: Int
    get() = 0

  /** Custom external templates contributed by this plugin. */
  fun getExternalTemplates(): List<ExternalTemplateSpec> = emptyList()
}
