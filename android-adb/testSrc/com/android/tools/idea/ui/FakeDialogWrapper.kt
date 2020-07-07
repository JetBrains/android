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
import javax.swing.JComponent

/**
 * A [AbstractDialogWrapper] implementation with minimal implementation (i.e. "fake") to be used for unit testing.
 *
 * Use the [setUpFactory] method or the [FakeDialogWrapperRule] rule to override the default factory with the factory
 * for this class.
 */
class FakeDialogWrapper : AbstractDialogWrapper() {
  override var title: String = ""
  override var isModal: Boolean = false
  override val disposable = Disposer.newDisposable()
  override var cancelButtonText: String? = "Cancel"
  override var hideOkButton: Boolean = false
  var initCalled = false
  var showCalled = false
  var panel: JComponent? = null

  override fun init() {
    initCalled = true
  }

  override fun show() {
    panel = centerPanelProvider()
    showCalled = true
  }

  companion object {
    private val ourFakeFactory = object : DialogWrapperFactory {
      override fun createDialogWrapper(project: Project,
                                       canBeParent: Boolean,
                                       ideModalityType: DialogWrapper.IdeModalityType): AbstractDialogWrapper {
        val result = FakeDialogWrapper()
        ourLastInstance = result
        return result
      }
    }

    var ourLastInstance : FakeDialogWrapper? = null

    fun setUpFactory() : DialogWrapperFactory {
      val previous = factory
      factory = ourFakeFactory
      return previous
    }

    fun restoreFactory(previous: DialogWrapperFactory) {
      factory = previous
    }
  }
}
