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
package com.android.tools.idea.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JRootPane

/**
 * A simplified version of the [DialogWrapper] class from the IntelliJ platform.
 */
class SimpleDialog(private val options: SimpleDialogOptions) {
  companion object {
    /**
     * Returns the [SimpleDialog] that created the [DialogWrapper] passed as input
     */
    fun fromDialogWrapper(dialogWrapper: DialogWrapper): SimpleDialog? {
      return if (dialogWrapper is DialogWrapperInner) dialogWrapper.outerInstance else null
    }
  }

  private val innerDialogWrapper: DialogWrapperInner by lazy {
    DialogWrapperInner()
  }

  /**
   * The [Disposable] that can be used with [Disposer.register] when this dialog is closed or disposed
   */
  val disposable = Disposer.newDisposable()

  /**
   * The title of the dialog
   */
  var title: String
    get() = innerDialogWrapper.title
    set(value) { innerDialogWrapper.title = value }

  /**
   * The text of the `Cancel` button (default is "Cancel")
   */
  var cancelButtonText: String
    get() = innerDialogWrapper.cancelAction.getValue(Action.NAME)?.toString() ?: ""
    set(value) { innerDialogWrapper.cancelAction.putValue(Action.NAME, value) }

  /**
   * Should the `Cancel` button be visible
   */
  var cancelButtonVisible: Boolean
    get() = innerDialogWrapper.cancelButton?.isVisible ?: false
    set(value) { innerDialogWrapper.cancelButton?.isVisible = value }

  /**
   * Should the `Cancel` button be enabled
   */
  var cancelButtonEnabled: Boolean
    get() = innerDialogWrapper.cancelButton?.isEnabled ?: false
    set(value) { innerDialogWrapper.cancelButton?.isEnabled = value }

  /**
   * The text of the `OK` button (default is "OK")
   */
  var okButtonText: String
    get() = innerDialogWrapper.okAction.getValue(Action.NAME)?.toString() ?: ""
    set(value) { innerDialogWrapper.okAction.putValue(Action.NAME, value) }

  /**
   * Should the `Ok` button be shown
   */
  var okButtonVisible: Boolean
    get() = innerDialogWrapper.okButton?.isVisible ?: false
    set(value) { innerDialogWrapper.okButton?.isVisible = value }

  /**
   * Should the `Ok` button be enabled
   */
  var okButtonEnabled: Boolean
    get() = innerDialogWrapper.okButton?.isEnabled ?: false
    set(value) { innerDialogWrapper.okButton?.isEnabled = value }

  /**
   * The standard [JRootPane] container of this dialog
   */
  val rootPane : JRootPane
    get() = innerDialogWrapper.rootPane

  /**
   * The application specific [JComponent] used for the main content of the dialog
   */
  val contentPanel : JComponent
    get() = innerDialogWrapper.contentPanel

  fun init() {
    // Ensure we are disposed if our inner dialog is disposed (e.g. "Close" or "Cancel" button)
    Disposer.register(innerDialogWrapper.disposable, disposable)
    innerDialogWrapper.init()
  }

  fun show() {
    innerDialogWrapper.show()
  }

  private inner class DialogWrapperInner
    : DialogWrapper(options.project, options.canBeParent, options.ideModalityType) {

    public override fun init() {
      options.preferredFocusProvider()?.let { super.myPreferredFocusedComponent = it }
      options.cancelButtonText?.let { setCancelButtonText(it) }
      options.okButtonText?.let { setOKButtonText(it) }
      if (!options.hasOkButton) {
        cancelAction.putValue(DEFAULT_ACTION, true)
      }
      isModal = options.isModal
      title = options.title
      super.init()
    }

    val outerInstance: SimpleDialog
      get() = this@SimpleDialog

    /** Make [DialogWrapper.getOKAction] publicly accessible */
    val okAction = super.getOKAction()

    /** Make [DialogWrapper.getCancelAction] publicly accessible */
    @get:JvmName("getCancelAction_")
    val cancelAction = super.getCancelAction()

    val okButton: JButton?
      get() {
        return getButton(okAction)
      }

    val cancelButton: JButton?
      get() {
        return getButton(cancelAction)
      }

    override fun createCenterPanel(): JComponent {
      return options.centerPanelProvider()
    }

    override fun createActions(): Array<Action> {
      val helpAction = helpAction
      if (!options.hasOkButton) {
        return if (helpAction === myHelpAction && helpId == null) arrayOf(cancelAction)
        else arrayOf(cancelAction, helpAction)
      }
      else {
        return if (helpAction === myHelpAction && helpId == null) arrayOf(okAction, cancelAction)
        else arrayOf(okAction, cancelAction, helpAction)
      }
    }

    override fun doOKAction() {
      val handled = options.okActionHandler()
      if (!handled) {
        super.doOKAction()
      }
    }

    override fun doValidate(): ValidationInfo? {
      return options.validationHandler()
    }
  }
}
