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
package com.android.tools.idea.npw.ideahost

import com.android.tools.idea.help.StudioHelpManagerImpl
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.ui.wizard.StudioWizardLayout
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.intellij.ide.util.newProjectWizard.WizardDelegate
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.wizard.AbstractWizard
import com.intellij.openapi.Disposable
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
 * [IdeaWizardAdapter] does not need to handle the more general case of embedding any wizard (i.e. different cancellation policies etc.).
 */
internal class IdeaWizardAdapter(
  private val hostWizard: AbstractWizard<*>, private val guestWizard: ModelWizard
) : ModelWizard.WizardListener, WizardDelegate, Disposable {
  private val listeners = ListenerManager()
  private val customLayout = StudioWizardLayout()

  /**
   * Returns a [ModuleWizardStep] that embeds the guest wizard, for use in the host wizard.
   */
  val proxyStep: ModuleWizardStep
    get() = object : ModuleWizardStep() {
      override fun getComponent(): JComponent = customLayout.decorate(guestWizard.titleHeader, guestWizard.contentPanel)
      override fun updateDataModel() {
        // Not required as the guest wizard is using its own data model, updated via bindings.
      }
      override fun getHelpId(): String? = StudioHelpManagerImpl.STUDIO_HELP_PREFIX + "studio/projects/create-project.html"
    }

  init {
    guestWizard.addResultListener(this)
    listeners.listenAll(guestWizard.canGoBack(), guestWizard.canGoForward(), guestWizard.onLastStep())
      .withAndFire { this.updateButtons() }

    Disposer.register(hostWizard.disposable, this)
    Disposer.register(this, guestWizard)
    Disposer.register(this, customLayout)
  }

  /**
   * Update the buttons on the host wizard to reflect the state of the guest wizard
   */
  private fun updateButtons() {
    hostWizard.updateButtons(guestWizard.onLastStep().get(), guestWizard.canGoForward().get(), !guestWizard.canGoBack().get())
  }

  override fun onWizardFinished(result: ModelWizard.WizardResult) {
    hostWizard.close(DialogWrapper.CLOSE_EXIT_CODE, result.isFinished)
  }

  override fun onWizardAdvanceError(e: Exception) {
    DialogEarthquakeShaker.shake(hostWizard.window)
  }

  override fun doNextAction() {
    assert(guestWizard.canGoForward().get())
    guestWizard.goForward()
    updateButtons()
  }

  override fun doPreviousAction() {
    assert(guestWizard.canGoBack().get())
    guestWizard.goBack()
    updateButtons()
  }

  override fun doFinishAction() {
    assert(guestWizard.canGoForward().get())
    assert(guestWizard.onLastStep().get())
    guestWizard.goForward()
    updateButtons()
  }

  override fun canProceed(): Boolean = guestWizard.canGoForward().get()

  override fun dispose() {
    listeners.releaseAll()
    guestWizard.removeResultListener(this)
  }
}

