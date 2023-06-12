/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.idea.wizard.ui.WizardUtils.wrapWithVScroll
import com.intellij.ide.customize.CustomizeUIThemeStepPanel
import com.intellij.ide.ui.laf.darcula.DarculaInstaller
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

/**
 * Step for FirstRunWizard for selecting a color scheme.
 */
class SelectThemeStep : ModelWizardStep.WithoutModel("Select UI Theme") {
  private val themePanel: CustomizeUIThemeStepPanel = CustomizeUIThemeStepPanel()
  private val root: JBScrollPane = wrapWithVScroll(themePanel)

  override fun onProceeding() {
    // This code is duplicated from LafManager.initComponent(). But our Welcome Wizard is started AFTER that call so we repeat it here.
    if (UIUtil.isUnderDarcula()) {
      DarculaInstaller.install()
    }
    else {
      DarculaInstaller.uninstall()
    }
  }

  override fun getPreferredFocusComponent(): JComponent? = themePanel

  override fun getComponent(): JComponent = root
}