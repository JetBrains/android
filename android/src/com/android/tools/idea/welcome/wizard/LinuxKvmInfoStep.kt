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
import com.intellij.ui.dsl.builder.panel

private const val KVM_DOCUMENTATION_URL = "https://developer.android.com/r/studio-ui/emulator-kvm-setup.html"

/**
 * Provides guidance for setting up KVM on Linux platform.
 */
class LinuxKvmInfoStep : ModelWizardStep.WithoutModel("Emulator Settings") {
    private val infoText = """
We have detected that your system can run the Android emulator in an accelerated performance mode.<br><br>
Linux-based systems support virtual machine acceleration through the KVM (Kernel-based Virtual Machine) software package.<br><br>
Search for install instructions for your particular Linux configuration 
(<a href='$KVM_DOCUMENTATION_URL'>Android KVM Linux Installation</a>) that KVM is enabled for faster Android emulator performance.
"""

  override fun getComponent() = panel { row { text(infoText) } }
}
