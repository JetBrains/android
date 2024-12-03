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
package com.android.tools.idea.welcome.wizard

import com.android.tools.idea.welcome.wizard.deprecated.LinuxKvmInfoStepForm
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.util.SystemInfo
import javax.swing.JComponent

/** Provides guidance for setting up KVM on Linux platform. */
class LinuxKvmInfoStep : ModelWizardStep.WithoutModel("Emulator Settings") {
  private val form = LinuxKvmInfoStepForm()

  override fun getComponent(): JComponent = form.root

  override fun getPreferredFocusComponent(): JComponent? = form.urlPane

  override fun shouldShow(): Boolean = SystemInfo.isLinux
}
