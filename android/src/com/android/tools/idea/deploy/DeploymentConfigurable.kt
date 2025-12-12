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
package com.android.tools.idea.deploy

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.android.util.AndroidBundle

class DeploymentConfigurable : BoundConfigurable(
  AndroidBundle.message("configurable.DeploymentConfigurable.displayName")
) {

  override fun createPanel(): DialogPanel {
    val settings = DeploymentConfiguration.getInstance()
    return panel {
      row {
        checkBox(AndroidBundle.message("deployment.configurable.checkbox.apply.changes.fail"))
          .bindSelected(settings::APPLY_CHANGES_FALLBACK_TO_RUN)
      }
      row {
        checkBox(AndroidBundle.message("deployment.configurable.checkbox.apply.code.changes.fail"))
          .bindSelected(settings::APPLY_CODE_CHANGES_FALLBACK_TO_RUN)
      }
      row {
        comment(AndroidBundle.message("deployment.configurable.text.automatic.rerun.condition"))
      }
    }
  }
}
