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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent

/**
 * Implementation of [AbstractDialogWrapper] that uses the [DialogWrapper] class from the platform.
 */
class DefaultDialogWrapper(private val project: Project, private val canBeParent: Boolean, private val ideModalityType: DialogWrapper.IdeModalityType) : AbstractDialogWrapper() {
  private var innerDialogWrapper: DialogWrapperInner? = null
  override var isModal = false
  override val disposable: Disposable
    get() = innerDialogWrapper!!.disposable
  override var title = ""
  override var okButtonText: String? = null

  override fun init() {
    innerDialogWrapper = DialogWrapperInner(project, canBeParent, ideModalityType)
    innerDialogWrapper?.init()
  }

  override fun show() {
    innerDialogWrapper?.show()
  }

  private inner class DialogWrapperInner(project: Project, canBeParent: Boolean, ideModalityType: IdeModalityType) : DialogWrapper(project, canBeParent, ideModalityType) {
    public override fun init() {
      this@DefaultDialogWrapper.okButtonText?.let { setOKButtonText(it) }
      isModal = this@DefaultDialogWrapper.isModal
      title = this@DefaultDialogWrapper.title
      super.init()
    }

    override fun createCenterPanel(): JComponent? {
      return centerPanelProvider()
    }
  }
}