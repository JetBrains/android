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

import com.android.tools.idea.welcome.wizard.deprecated.ProgressStepForm
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.text.StringUtil.shortenTextWithEllipsis
import javax.swing.JComponent

/**
 * Wizard step with progress bar and "more details" button.
 */
abstract class AbstractProgressStep<T: WizardModel>(model: T, parent: Disposable, name: String) : ModelWizardStep<T>(model, name), ProgressStep {
  private val form = ProgressStepForm(parent)
  private var myProgressIndicator: ProgressIndicator? = null

  public override fun getComponent(): JComponent = form.root

  override fun getPreferredFocusComponent(): JComponent? {
    return form.showDetailsButton
  }

  public override fun onEntering() {
    invokeLater { execute() }
  }

  protected abstract fun execute()

  /**
   * Returns progress indicator that will report the progress to this wizard step.
   */
  @Synchronized
  override fun getProgressIndicator(): ProgressIndicator {
    if (myProgressIndicator == null) {
      myProgressIndicator = ProgressIndicatorIntegration(form)
    }
    return myProgressIndicator!!
  }

  override fun isCanceled(): Boolean = getProgressIndicator().isCanceled

  fun isRunning(): Boolean = getProgressIndicator().isRunning

  /**
   * Output text to the console pane.
   *
   * @param s           text to print
   * @param contentType attributes of the text to output
   */
  override fun print(s: String, contentType: ConsoleViewContentType) {
    form.print(s, contentType)
  }

  /**
   * Will output process standard in and out to the console view.
   *
   *
   * Note: current version does not support collecting user input. We may
   * reconsider this at a later point.
   *
   * @param processHandler  process to track
   */
  override fun attachToProcess(processHandler: ProcessHandler) {
    form.attachToProcess(processHandler)
  }

  /**
   * Displays console widget if one was not visible already
   */
  fun showConsole() = form.showConsole()

  /**
   * Runs the computable under progress manager but only gives a portion of the progress bar to it.
   */
  override fun run(runnable: Runnable, progressPortion: Double) {
    val progress = ProgressPortionReporter(getProgressIndicator(), form.fraction, progressPortion)
    ProgressManager.getInstance().executeProcessUnderProgress(runnable, progress)
  }

  /**
   * Progress indicator that scales task to only use a portion of the parent indicator.
   */
  // TODO(qumeric): make private
  class ProgressPortionReporter(
    indicator: ProgressIndicator, private val start: Double, private val portion: Double
  ) : DelegatingProgressIndicator(indicator) {

    override fun start() {
      fraction = 0.0
    }

    override fun stop() {
      fraction = portion
    }

    override fun setFraction(fraction: Double) {
      super.setFraction(start + fraction * portion)
    }
  }

  /**
   * Progress indicator integration for this wizard step
   */
  class ProgressIndicatorIntegration(private val form: ProgressStepForm) : ProgressIndicatorBase() {
    override fun start() {
      super.start()
      isIndeterminate = false
    }

    override fun setText(text: String) = invokeLater(ModalityState.stateForComponent(form.label)) {
      form.label.text = text
    }

    override fun setText2(text: String?) = invokeLater(ModalityState.stateForComponent(form.label)) {
      form.label2.text = if (text == null) "" else shortenTextWithEllipsis(text, 80, 10)
    }

    override fun stop() {
      invokeLater(ModalityState.stateForComponent(form.progressBar)) {
        form.label.text = null
        form.label2.text = null
        form.progressBar.isVisible = false
        form.showConsole()
      }
      super.stop()
    }

    override fun setIndeterminate(indeterminate: Boolean) {
      invokeLater(ModalityState.stateForComponent(form.progressBar)) { form.progressBar.isIndeterminate = indeterminate }
    }

    override fun setFraction(fraction: Double) {
      invokeLater(ModalityState.stateForComponent(form.root)) { form.fraction = fraction }
    }
  }
}