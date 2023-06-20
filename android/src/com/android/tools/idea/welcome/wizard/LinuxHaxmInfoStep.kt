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

import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.ui.layout.panel
import javax.swing.JComponent

private const val KVM_DOCUMENTATION_URL = "http://developer.android.com/r/studio-ui/emulator-kvm-setup.html"

/**
 * Provides guidance for setting up Intel® HAXM on Linux platform.
 */
class LinuxHaxmInfoStep : ModelWizardStep.WithoutModel("Emulator Settings") {
  override fun getComponent(): JComponent = panel {
    row("We have detected that your system can run the Android emulator in an accelerated performance mode.") {}
    row("Linux-based systems support virtual machine acceleration through the KVM (Kernel-based Virtual Machine) software package.") {}
    row {
      cell {
        label("Search for install instructions for your particular Linux configuration (")
        browserLink("Android KVM Linux Installation", KVM_DOCUMENTATION_URL)
        label(") that KVM is enabled for faster Android emulator performance.")
      }
    }
  }
}