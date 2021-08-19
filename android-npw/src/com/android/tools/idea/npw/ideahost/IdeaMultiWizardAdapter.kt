/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.npw.ideahost

import com.android.tools.idea.help.AndroidWebHelpProvider
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.intellij.ide.util.newProjectWizard.WizardDelegate
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.wizard.AbstractWizard
import com.intellij.openapi.ui.DialogEarthquakeShaker
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

/**
 * Provides an implementation of the [WizardDelegate] interface (which plugs in to the IntelliJ IDEA New Project / Module Wizards)
 * that hosts the [ModelWizard] based AndroidStudio New Project and New Module wizards.
 *
 * In Android Studio, wizards are hosted in the [ModelWizardDialog] class, however when running as a plugin in IDEA we do not create
 * the hosting dialog, but instead need to embed the wizard inside an existing dialog. This class manages that embedding. The
 * [WizardDelegate] class is specific to the IDEA New Project Wizard (see [AndroidModuleBuilder] for more details) so the
 * [IdeaMultiWizardAdapter] does not need to handle the more general case of embedding any wizard (i.e. different cancellation policies etc.).
 */
class IdeaMultiWizardAdapter(
  private val ideaWizard: AbstractWizard<*>,
  private val mainComponent: JComponent
) : ModelWizard.WizardListener, IdeaWizardDelegate {
  private val listeners = ListenerManager()
  private var currentModelWizard: ModelWizard? = null

  /**
   * Returns a [ModuleWizardStep] that embeds the guest wizard, for use in the host wizard.
   */
  private val proxyStep: ModuleWizardStep = object : ModuleWizardStep() {
      override fun getComponent(): JComponent = mainComponent
      override fun updateDataModel() {
        // Not required as the guest wizard is using its own data model, updated via bindings.
      }
      override fun getHelpId(): String? = AndroidWebHelpProvider.HELP_PREFIX + "studio/projects/create-project.html"
  }

  init {
    Disposer.register(ideaWizard.disposable, this)
  }

  override fun setModelWizard(modelWizard: ModelWizard) {
    disposeCurrentModelWizard()

    currentModelWizard = modelWizard

    modelWizard.apply {
      addResultListener(this@IdeaMultiWizardAdapter)
      listeners.listenAll(canGoBack(), canGoForward(), onLastStep())
        .withAndFire { updateButtons() }
    }
  }

  override fun getCustomOptionsStep(): ModuleWizardStep  = proxyStep

  /**
   * Update the buttons on the host wizard to reflect the state of the guest wizard
   */
  override fun updateButtons() {
    currentModelWizard?.apply {
      ideaWizard.updateButtons(onLastStep().get(), canGoForward().get(), !canGoBack().get())
    }
  }

  override fun onWizardFinished(result: ModelWizard.WizardResult) {
    ideaWizard.close(DialogWrapper.CLOSE_EXIT_CODE, result.isFinished)
  }

  override fun onWizardAdvanceError(e: Exception) {
    DialogEarthquakeShaker.shake(ideaWizard.window)
  }

  override fun doNextAction() {
    currentModelWizard?.apply {
      assert(canGoForward().get())
      goForward()
      updateButtons()
    }
  }

  override fun doPreviousAction() {
    currentModelWizard?.apply {
      assert(canGoBack().get())
      goBack()
      updateButtons()
    }
  }

  override fun doFinishAction() {
    currentModelWizard?.apply {
      assert(canGoForward().get())
      assert(onLastStep().get())
      goForward()
      updateButtons()
    }
  }

  override fun canProceed(): Boolean = currentModelWizard?.canGoForward()?.get() ?: false

  override fun dispose() {
    disposeCurrentModelWizard()
  }

  private fun disposeCurrentModelWizard() {
    currentModelWizard?.apply {
      removeResultListener(this@IdeaMultiWizardAdapter)
      listeners.releaseAll()
      Disposer.dispose(this)
    }
  }
}
