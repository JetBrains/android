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

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Thumb

/**
 * Describes a placeholder entry in the new-project wizard which advertises the installation of another plugin.
 *
 * Unlike a regular [com.android.tools.idea.wizard.template.Template], a promotion template does not generate any
 * project files. When the user picks it, the wizard offers to install the plugin identified by [pluginId] instead.
 **/
interface PluginPromotionTemplate {
  /**
   * A template name which is also used as an identifier.
   */
  val name: String

  /**
   * ID of the plugin which the wizard offers to install when the user picks this template.
   */
  val pluginId: String

  /** Returns a thumbnail which are drawn in the UI. It will be called every time when any parameter is updated. */
  // TODO(qumeric): consider using IconLoader and/or wizard icons.
  fun thumb(): Thumb

  /**
   * Determines to which form factor the template belongs. Templates with particular form factor may only be rendered in the
   * project of corresponding [Category].
   */
  val formFactor: FormFactor
}
