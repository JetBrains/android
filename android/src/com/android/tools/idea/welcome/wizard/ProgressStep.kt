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
import com.android.tools.idea.wizard.template.stripSuffix
import com.android.tools.idea.wizard.ui.WizardUtils.wrapWithVScroll
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil.shortenTextWithEllipsis
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar

private val any = Dimension(-1, -1)

/**
 * Wizard step with progress bar and "more details" button.
 */
abstract class ProgressStep(parent: Disposable, name: String) : ModelWizardStep.WithoutModel(name) {
  private val highlighter = ConsoleHighlighter().apply {
    setModalityState(ModalityState.stateForComponent(label))
  }
  private val consoleEditor: EditorEx = ConsoleViewUtil.setupConsoleEditor(null, false, false).apply {
    settings.isUseSoftWraps = true
    reinitSettings()
    setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 0))
    Disposer.register(parent, Disposable { EditorFactory.getInstance().releaseEditor(this) })
    highlighter = this@ProgressStep.highlighter
  }
  private val label = JBLabel("Installing")
  private val label2 = JBLabel()
  private val progressBar = JProgressBar().apply {
    isIndeterminate = true
  }
  // TODO(qumeric): add hspacers
  private val innerPanel = JPanel(GridLayoutManager(3, 1)).apply {
    add(label, GridConstraints(0, 0, 1, 1, 0, 1, 0, 0, any, any, any))
    add(label2, GridConstraints(1, 0, 1, 1, 0, 1, 0, 0, any, any, any))
    add(progressBar, GridConstraints(2, 0, 1, 1, 0, 1, 0, 0, any, any, any))
  }
  private val showDetailsButton: JButton = JButton("Show Details").apply {
    addActionListener { showConsole() }
  }
  private val console = BorderLayoutPanel(0, 0).apply {
    add(consoleEditor.component.apply { isVisible = false }, BorderLayout.CENTER)
  }
  private val outerPanel = JPanel(GridLayoutManager(3, 3)).apply {
    add(innerPanel, GridConstraints(0, 1, 1, 1, 0, 3, 3, 3, any, any, any))
    add(showDetailsButton, GridConstraints(1, 1, 1, 1, 8, 0, 3, 1, any, any, any))
    add(console, GridConstraints(2, 1, 1, 1, 0, 3, 7, 7, any, any, any))
  }
  private var myProgressIndicator: ProgressIndicator? = null
  private var fraction = 0.0
  private val root: JBScrollPane = wrapWithVScroll(outerPanel)

  // TODO (what is this?) @Override
  //public JComponent getPreferredFocusedComponent() {
  //  return myShowDetailsButton;
  //}

  /**
   * Returns progress indicator that will report the progress to this wizard step.
   */
  val progressIndicator: ProgressIndicator
    @Synchronized get() {
      if (myProgressIndicator == null) {
        myProgressIndicator = ProgressIndicatorIntegration()
      }
      return myProgressIndicator!!
    }

  val isCanceled: Boolean
    get() = progressIndicator.isCanceled

  public override fun getComponent(): JComponent = root

  public override fun onEntering() {
    invokeLater { execute() }
  }

  protected abstract fun execute()

  /**
   * Output text to the console pane.
   *
   * @param s           text to print
   * @param contentType attributes of the text to output
   */
  fun print(s: String, contentType: ConsoleViewContentType) = with(highlighter) {
    setModalityState(ModalityState.stateForComponent(console))
    print(s.stripSuffix("\n") + '\n', contentType.attributes)
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
  fun attachToProcess(processHandler: ProcessHandler) {
    highlighter.attachToProcess(processHandler)
  }

  /**
   * Displays console widget if one was not visible already
   */
  fun showConsole() = invokeLater {
    val editorComponent = consoleEditor.component
    if (!editorComponent.isVisible) {
      showDetailsButton.parent.remove(showDetailsButton)
      editorComponent.isVisible = true
    }
  }

  /**
   * Runs the computable under progress manager but only gives a portion of the progress bar to it.
   */
  fun run(runnable: Runnable, progressPortion: Double) {
    val progress = ProgressPortionReporter(progressIndicator, fraction, progressPortion)
    ProgressManager.getInstance().executeProcessUnderProgress(runnable, progress)
  }

  private fun setFraction(fraction: Double) {
    this.fraction = fraction
    progressBar.maximum = 1000
    progressBar.value = (1000 * fraction).toInt()
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
  private inner class ProgressIndicatorIntegration : ProgressIndicatorBase() {
    override fun start() {
      super.start()
      isIndeterminate = false
    }

    override fun setText(text: String) = invokeLater {
      label.text = text
    }

    override fun setText2(text: String?) = invokeLater {
      label2.text = if (text == null) "" else shortenTextWithEllipsis(text, 80, 10)
    }

    override fun stop() {
      invokeLater(ModalityState.stateForComponent(progressBar)) {
        label.text = null
        progressBar.isVisible = false
        showConsole()
      }
      super.stop()
    }

    override fun setIndeterminate(indeterminate: Boolean) {
      invokeLater { progressBar.isIndeterminate = indeterminate }
    }

    override fun setFraction(fraction: Double) {
      invokeLater { this@ProgressStep.setFraction(fraction) }
    }
  }
}