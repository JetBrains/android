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

import com.android.tools.idea.help.AndroidWebHelpProvider
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.android.tools.idea.wizard.ui.StudioWizardLayout
import com.intellij.ide.util.newProjectWizard.WizardDelegate
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.wizard.AbstractWizard
import com.intellij.openapi.ui.DialogEarthquakeShaker
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants
import com.intellij.util.ui.JBUI
/**
 * Provides an implementation of the [WizardDelegate] interface (which plugs in to the IntelliJ IDEA New Project / Module Wizards) that
 * hosts the [ModelWizard] based AndroidStudio New Project and New Module wizards.
 *
 * In Android Studio, wizards are hosted in the [ModelWizardDialog] class, however when running as a plugin in IDEA we do not create the
 * hosting dialog, but instead need to embed the wizard inside an existing dialog. This class manages that embedding. The [WizardDelegate]
 * class is specific to the IDEA New Project Wizard (see [AndroidModuleBuilder] for more details) so the [IdeaWizardAdapter] does not need
 * to handle the more general case of embedding any wizard (i.e. different cancellation policies etc.).
 */
class IdeaWizardAdapter(private val ideaWizard: AbstractWizard<*>, private val modelWizard: ModelWizard) :
  ModelWizard.WizardListener, IdeaWizardDelegate {
  private val listeners = ListenerManager()
  private val customLayout = StudioWizardLayout()

  /** Returns a [ModuleWizardStep] that embeds the guest wizard, for use in the host wizard. */
  private val proxyStep: ModuleWizardStep
    get() =
      object : ModuleWizardStep() {
        private val hostComponent: JComponent by lazy {
          ViewportTrackingPanel(
            content = customLayout.decorate(modelWizard.titleHeader, modelWizard.contentPanel),
            minSize = customLayout.defaultMinSize,
            prefSize = customLayout.defaultPreferredSize,
          )
        }

        override fun getComponent(): JComponent = hostComponent

        override fun updateDataModel() {
          // Not required as the guest wizard is using its own data model, updated via bindings.
        }

        override fun getHelpId(): String? = AndroidWebHelpProvider.HELP_PREFIX + "studio/projects/create-project.html"
      }

  /**
   * Hosts the guest wizard inside IDEA's [ProjectTypeStep] scroll pane.
   *
   * The explicit preferred size keeps Swing from asking the Compose panel to measure itself with unbounded constraints
   * (the NPW content is lazy lists, which cannot be measured against an infinite height). [Scrollable] then lets the view
   * follow the viewport, so the wizard grows and shrinks with the dialog and only scrolls below [minSize].
   */
  private class ViewportTrackingPanel(content: JComponent, minSize: Dimension, prefSize: Dimension) :
    JPanel(BorderLayout()), Scrollable {

    private val initialSize = prefSize

    init {
      add(content, BorderLayout.CENTER)
      minimumSize = minSize
      preferredSize = prefSize   // keeps isPreferredSizeSet true, so nothing ever asks the Compose panel
    }

    private val viewport: JViewport?
      get() = parent as? JViewport

    /** Fill the viewport; below the minimum size, stop at the minimum and let the scroll pane scroll. */
    override fun getPreferredSize(): Dimension {
      val viewport = viewport ?: return initialSize
      return Dimension(
        maxOf(viewport.width, minimumSize.width),
        maxOf(viewport.height, minimumSize.height),
      )
    }

    override fun getPreferredScrollableViewportSize(): Dimension = initialSize

    override fun getScrollableTracksViewportWidth(): Boolean = viewport?.let { it.width >= minimumSize.width } ?: true
    override fun getScrollableTracksViewportHeight(): Boolean = viewport?.let { it.height >= minimumSize.height } ?: true

    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
      if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width
  }
  init {
    modelWizard.addResultListener(this)
    listeners.listenAll(modelWizard.canGoBack(), modelWizard.canGoForward(), modelWizard.onLastStep()).withAndFire { this.updateButtons() }

    Disposer.register(ideaWizard.disposable, this)
    Disposer.register(this, modelWizard)
    Disposer.register(this, customLayout)
  }

  override fun getCustomOptionsStep(): ModuleWizardStep = proxyStep

  /** Update the buttons on the host wizard to reflect the state of the guest wizard */
  override fun updateButtons() {
    ideaWizard.updateButtons(modelWizard.onLastStep().get(), modelWizard.canGoForward().get(), !modelWizard.canGoBack().get())
  }

  override fun onWizardFinished(result: ModelWizard.WizardResult) {
    ideaWizard.close(DialogWrapper.CLOSE_EXIT_CODE, result.isFinished)
  }

  override fun onWizardAdvanceError(e: Exception) {
    DialogEarthquakeShaker.shake(ideaWizard.window)
  }

  override fun doNextAction() {
    assert(modelWizard.canGoForward().get())
    modelWizard.goForward()
    updateButtons()
  }

  override fun doPreviousAction() {
    assert(modelWizard.canGoBack().get())
    modelWizard.goBack()
    updateButtons()
  }

  override fun doFinishAction() {
    assert(modelWizard.canGoForward().get())
    assert(modelWizard.onLastStep().get())
    modelWizard.goForward()
    updateButtons()
  }

  override fun canProceed(): Boolean = modelWizard.canGoForward().get()

  override fun dispose() {
    listeners.releaseAll()
    modelWizard.removeResultListener(this)
  }
}
