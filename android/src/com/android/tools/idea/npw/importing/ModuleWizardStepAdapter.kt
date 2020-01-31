/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.importing

import com.android.tools.idea.gradle.project.ModuleImporter
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.CommitStepException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ConfigurationException
import org.jetbrains.annotations.TestOnly
import javax.swing.JComponent

@TestOnly
var logForTesting: Logger? = null
private val log: Logger get() = logForTesting ?: logger<ModuleWizardStepAdapter>()

/**
 * Presents an Idea [ModuleWizardStep] based class as a [ModelWizardStep]
 */
class ModuleWizardStepAdapter(
  private val context: WizardContext,
  private val wrappedStep: ModuleWizardStep
) : ModelWizardStep<ModuleWizardStepAdapter.AdapterModel>(AdapterModel(wrappedStep), wrappedStep.name) {
  private val canGoForward: BoolProperty = BoolValueProperty()

  init {
    wrappedStep.registerStepListener { updateCanGoForward() }
  }

  override fun dispose() {
    wrappedStep.disposeUIResources()
    super.dispose()
  }

  public override fun getComponent(): JComponent = wrappedStep.component

  override fun shouldShow(): Boolean =
    wrappedStep.isStepVisible.takeIf { ModuleImporter.getImporter(context).isStepVisible(wrappedStep) } ?: false

  public override fun onEntering() = updateCanGoForward()

  private fun updateCanGoForward() {
    try {
      canGoForward.set(wrappedStep.validate())
    }
    catch (e: ConfigurationException) {
      canGoForward.set(false)
    }
  }

  public override fun onProceeding() {
    wrappedStep.updateDataModel()
    wrappedStep.onStepLeaving()
  }

  public override fun canGoForward(): ObservableBool = canGoForward

  /**
   * Model used to trigger onWizardFinished event when Wizard completes
   */
  class AdapterModel(private val step: ModuleWizardStep) : WizardModel() {
    public override fun handleFinished() {
      try {
        step.onWizardFinished()
      }
      catch (e: CommitStepException) {
        log.error(e.message)
      }
    }
  }
}
