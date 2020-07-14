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

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import javax.swing.Action
import javax.swing.JComponent

/**
 * Implementation of [AbstractDialogWrapper] that uses the [DialogWrapper] class from the platform.
 */
class DefaultDialogWrapper(private val project: Project, private val canBeParent: Boolean, private val ideModalityType: DialogWrapper.IdeModalityType) : AbstractDialogWrapper() {
  private val innerDialogWrapper: DialogWrapperInner by lazy {
    DialogWrapperInner(project, canBeParent, ideModalityType)
  }
  override var isModal = false
  override val disposable = Disposer.newDisposable()
  override var title = ""
  override var cancelButtonText: String? = null
  override var hideOkButton = false

  override fun init() {
    // Ensure we are disposed if our inner dialog is disposed (e.g. "Close" or "Cancel" button)
    Disposer.register(innerDialogWrapper.disposable, disposable)
    innerDialogWrapper.init()
  }

  override fun show() {
    innerDialogWrapper.show()
  }

  private inner class DialogWrapperInner(project: Project, canBeParent: Boolean, ideModalityType: IdeModalityType) : DialogWrapper(project, canBeParent, ideModalityType) {
    public override fun init() {
      this@DefaultDialogWrapper.cancelButtonText?.let { setCancelButtonText(it) }
      if (hideOkButton) {
        cancelAction.putValue(DEFAULT_ACTION, true)
      }
      isModal = this@DefaultDialogWrapper.isModal
      title = this@DefaultDialogWrapper.title
      super.init()
    }

    override fun createCenterPanel(): JComponent? {
      return centerPanelProvider()
    }

    override fun createActions(): Array<Action> {
      val helpAction = helpAction
      if (hideOkButton) {
        return if (helpAction === myHelpAction && helpId == null) arrayOf(cancelAction)
        else arrayOf(cancelAction, helpAction)
      }
      else {
        return if (helpAction === myHelpAction && helpId == null) arrayOf(okAction, cancelAction)
        else arrayOf(okAction, cancelAction, helpAction)
      }
    }
  }
}