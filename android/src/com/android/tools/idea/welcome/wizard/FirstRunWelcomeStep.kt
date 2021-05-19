/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.ui.wizard.WizardUtils.wrapWithVScroll
import com.android.tools.idea.util.getFormFactorsImage
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.panel
import com.intellij.uiDesigner.core.Spacer
import javax.swing.JComponent

/**
 * Welcome page for the first run wizard
 */
class FirstRunWelcomeStep(model: FirstRunModel) : ModelWizardStep<FirstRunModel>(model, "Welcome") {
  private val newSdkMessage: DialogPanel = panel {
    row {
      label("""Welcome! This wizard will set up your development environment for Android Studio.
Additionally, the wizard will help port existing Android apps into Android Studio
or create a new Android application project.
""")
    }
  }

  private val existingSdkMessage: DialogPanel = panel {
    row {
      label("""Welcome back! This setup wizard will validate your current Android SDK and
development environment setup. You will have the option to download a new Android
SDK or use an existing installation. Once the setup wizard completes, you can
import an existing Android app into Android Studio or start a new Android project.
""")
    }
  }

  private val icons = JBLabel("").apply {
    icon = getFormFactorsImage(this, false)
  }

  private val panel = panel {
    row {
      Spacer()()
    }
    row {
      if (model.sdkExists) {
        existingSdkMessage()
      } else {
        newSdkMessage()
      }
    }
    row {
      Spacer()()
    }
    row {
      icons()
    }
    row {
      Spacer()()
    }
  }

  private val root  = wrapWithVScroll(panel)

  override fun getComponent() = root
  override fun getPreferredFocusComponent(): JComponent? = null
}

