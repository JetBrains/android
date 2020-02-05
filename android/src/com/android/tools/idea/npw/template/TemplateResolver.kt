/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.WizardTemplateProvider
import com.intellij.openapi.extensions.ExtensionPointName

class TemplateResolver {

  companion object {
    private val EP_NAME = ExtensionPointName<WizardTemplateProvider>("com.android.tools.idea.wizard.template.wizardTemplateProvider")

    fun getAllTemplates(): List<Template> {
      return EP_NAME.extensions
        .flatMap { it.getTemplates() }
        .filter { it.category != Category.Compose || StudioFlags.COMPOSE_WIZARD_TEMPLATES.get()}
    }

    fun getTemplateByName(name: String) = getAllTemplates().find { it.name == name }
  }
}
