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

import com.android.sdklib.devices.Storage
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.IntProperty
import com.android.tools.idea.observable.ui.SliderValueProperty
import com.android.tools.idea.observable.ui.SpinnerValueProperty
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.StartupUiUtil
import java.awt.Dimension
import java.awt.Font
import java.util.Hashtable
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider
import kotlin.math.abs

/**
 * Wizard page for setting up AEHD settings
 */
class AehdInstallInfoStep(
) : ModelWizardStep.WithoutModel("Emulator Settings") {
  private val panel = panel {
    row {
      label("<html>This wizard will execute Android Emulator hypervisor driver stand-alone installer."
            + " This is an additional step required to install this package.</html>")
    }
    row {
      label("Click 'Next' to proceed")
    }
  }

  override fun getComponent(): JComponent = panel
}
