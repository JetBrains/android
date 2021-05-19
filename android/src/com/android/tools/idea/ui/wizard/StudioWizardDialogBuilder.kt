/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.ui.wizard

import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizard.Builder
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.android.tools.idea.wizard.model.ModelWizardDialog.CancellationPolicy
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import java.awt.Component
import java.awt.Dimension
import java.awt.Toolkit
import java.net.URL

/**
 * Convenience class for building a [ModelWizard] styled for Android Studio.
 */
class StudioWizardDialogBuilder(internal var wizard: ModelWizard, internal var title: String) {
  internal var parent: Component? = null
  internal var project: Project? = null
  private var helpUrl: URL? = null
  private var modalityType = IdeModalityType.IDE
  private var minimumSize: Dimension? = null
  private var preferredSize: Dimension? = null
  private var cancellationPolicy = CancellationPolicy.ALWAYS_CAN_CANCEL

  /**
   * Convenience construction for wizards that only have a single step in them.
   */
  constructor(step: ModelWizardStep<*>, title: String) : this(Builder(step).build(), title)

  /**
   * Build a wizard with a parent component it should always show in front of. If you use this
   * constructor, any calls to [setProject] and
   * [setModalityType] will be ignored.
   */
  constructor(wizard: ModelWizard, title: String, parent: Component?) : this(wizard, title) {
    this.parent = parent
  }

  /**
   * Set the target project that this dialog is associated with.
   *
   * If `null`, this call will be ignored, although it is allowed as an argument to work well with `Nullable` APIs.
   */
  fun setProject(project: Project?): StudioWizardDialogBuilder {
    if (project != null) {
      this.project = project
    }
    return this
  }

  /**
   * Override the modality type of this dialog.
   *
   * If `null`, this call will be ignored, although it is allowed as an argument to work well with `Nullable` APIs.
   */
  fun setModalityType(modalityType: IdeModalityType?): StudioWizardDialogBuilder {
    if (modalityType != null) {
      this.modalityType = modalityType
    }
    return this
  }

  /**
   * Override the minimum size of this dialog.
   *
   * If `null`, this call will be ignored, although it is allowed as an argument to work well with `Nullable` APIs.
   */
  fun setMinimumSize(minimumSize: Dimension?): StudioWizardDialogBuilder {
    if (minimumSize != null) {
      this.minimumSize = minimumSize
    }
    return this
  }

  /**
   * Override the preferred size of this dialog.
   *
   * If `null`, this call will be ignored, although it is allowed as an argument to work well with `Nullable` APIs.
   */
  fun setPreferredSize(preferredSize: Dimension?): StudioWizardDialogBuilder {
    if (preferredSize != null) {
      this.preferredSize = preferredSize
    }
    return this
  }

  /**
   * Set a help link that the dialog's help button should browse to.
   *
   * If `null`, this call will be ignored, although it is allowed as an argument to work well with `Nullable` APIs.
   */
  fun setHelpUrl(helpUrl: URL?): StudioWizardDialogBuilder {
    if (helpUrl != null) {
      this.helpUrl = helpUrl
    }
    return this
  }

  /**
   * Set the dialog cancellation policy to provide more fine-grained user experience
   * by making it clearer when clicking Cancel is likely to incur some actual cancellation action
   *
   * If `null`, this call will be ignored, although it is allowed as an argument to work well with `Nullable` APIs.
   */
  fun setCancellationPolicy(cancellationPolicy: CancellationPolicy?): StudioWizardDialogBuilder {
    if (cancellationPolicy != null) {
      this.cancellationPolicy = cancellationPolicy
    }
    return this
  }

  fun build(customLayout: ModelWizardDialog.CustomLayout): ModelWizardDialog {
    minimumSize = minimumSize ?: customLayout.defaultMinSize
    preferredSize = preferredSize ?: customLayout.defaultPreferredSize
    val dialog: ModelWizardDialog = if (parent != null)
      ModelWizardDialog(wizard, title, parent!!, customLayout, helpUrl, cancellationPolicy)
    else
      ModelWizardDialog(wizard, title, customLayout, project, helpUrl, modalityType, cancellationPolicy)
    val contentPanel = dialog.contentPanel
    if (contentPanel != null) {
      contentPanel.minimumSize = getClampedSize(minimumSize!!)
      contentPanel.preferredSize = getClampedSize(preferredSize!!)
    }
    return dialog
  }

  fun build(): ModelWizardDialog = build(StudioWizardLayout())
}

/**
 * The minimum (and initial) size of a dialog should be no bigger than the user's screen (or,
 * a percentage of the user's screen, to leave a bit of space on the sides). This prevents
 * developers from specifying a size that looks good on their monitor but won't fit on a low
 * resolution screen. Worst case, the UI may end up squished for some users, but the
 * prev/next/cancel buttons will always be visible.
 */
private const val SCREEN_PERCENT = 0.8f

private fun getClampedSize(size: Dimension): Dimension {
  val screenSize: Dimension = Toolkit.getDefaultToolkit().screenSize
  return Dimension(size.width.coerceAtMost((screenSize.width * SCREEN_PERCENT).toInt()),
                   size.height.coerceAtMost((screenSize.height * SCREEN_PERCENT).toInt()))
}
