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
package com.android.tools.idea.compose.preview.runconfiguration

import com.android.tools.idea.run.composePreviewRunConfigurationId
import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NotNullLazyValue
import icons.StudioIcons

/** A type for run configurations that launch Compose Previews to a device/emulator. */
class ComposePreviewRunConfigurationType :
  SimpleConfigurationType(
    composePreviewRunConfigurationId,
    "Compose Preview",
    "Compose Preview Run Configuration Type",
    NotNullLazyValue.createValue { StudioIcons.Compose.Toolbar.RUN_CONFIGURATION }
  ) {
  override fun createTemplateConfiguration(project: Project) =
    ComposePreviewRunConfiguration(project, this)

  override fun configureBeforeRunTaskDefaults(
    providerID: Key<out BeforeRunTask<*>?>,
    task: BeforeRunTask<*>
  ) {
    // A specific build is executed as part of the ComposePreviewRunConfiguration logic,
    // and then the default build performed as BeforeRunTask should be disabled to avoid
    // executing two builds on each run.
    if (CompileStepBeforeRun.ID == providerID) {
      task.isEnabled = false
    }
  }
}
