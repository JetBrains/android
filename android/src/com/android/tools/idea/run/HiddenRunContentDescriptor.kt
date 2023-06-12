/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentDescriptorReusePolicy
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.Content
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * Provides a result RunContentDescriptor for Apply Code Changes and Apply Changes operation.
 * Rewrites [isHiddenContent] to true that forces [ExecutionManagerImpl] not to show [delegate] descriptor.
 */
internal class HiddenRunContentDescriptor constructor(private val delegate: RunContentDescriptor) :
  RunContentDescriptor(null, null, JLabel(), "hidden", null, null, null) {
  init {
    Disposer.register(this, delegate)
  }

  override fun getActivationCallback(): Runnable? = delegate.activationCallback

  override fun getRestartActions(): Array<AnAction> = delegate.restartActions

  override fun getExecutionConsole(): ExecutionConsole = delegate.executionConsole

  override fun dispose() {}
  override fun getIcon(): Icon? = delegate.icon

  override fun getProcessHandler(): ProcessHandler? = delegate.processHandler

  override fun setProcessHandler(processHandler: ProcessHandler) {
    delegate.processHandler = processHandler
  }

  override fun isContentReuseProhibited(): Boolean = delegate.isContentReuseProhibited

  override fun getComponent(): JComponent = delegate.component

  override fun getDisplayName(): String = delegate.displayName

  override fun getHelpId(): String = delegate.helpId

  override fun getAttachedContent(): Content? = delegate.attachedContent

  override fun setAttachedContent(content: Content) {
    delegate.setAttachedContent(content)
  }

  override fun getContentToolWindowId(): String? = delegate.contentToolWindowId

  override fun setContentToolWindowId(contentToolWindowId: String?) {
    delegate.contentToolWindowId = contentToolWindowId
  }

  override fun isActivateToolWindowWhenAdded(): Boolean = delegate.isActivateToolWindowWhenAdded

  override fun setActivateToolWindowWhenAdded(activateToolWindowWhenAdded: Boolean) {
    delegate.isActivateToolWindowWhenAdded = activateToolWindowWhenAdded
  }

  override fun isSelectContentWhenAdded(): Boolean = delegate.isSelectContentWhenAdded

  override fun setSelectContentWhenAdded(selectContentWhenAdded: Boolean) {
    delegate.isSelectContentWhenAdded = selectContentWhenAdded
  }

  override fun isReuseToolWindowActivation(): Boolean = delegate.isReuseToolWindowActivation

  override fun setReuseToolWindowActivation(reuseToolWindowActivation: Boolean) {
    delegate.isReuseToolWindowActivation = reuseToolWindowActivation
  }

  override fun getExecutionId(): Long = delegate.executionId

  override fun setExecutionId(executionId: Long) {
    delegate.executionId = executionId
  }

  override fun toString(): String = delegate.toString()

  override fun getPreferredFocusComputable(): Computable<JComponent> {
    return delegate.preferredFocusComputable
  }

  override fun setFocusComputable(focusComputable: Computable<JComponent>) {
    delegate.setFocusComputable(focusComputable)
  }

  override fun isAutoFocusContent(): Boolean = delegate.isAutoFocusContent

  override fun setAutoFocusContent(autoFocusContent: Boolean) {
    delegate.isAutoFocusContent = autoFocusContent
  }

  override fun getRunnerLayoutUi(): RunnerLayoutUi? = delegate.runnerLayoutUi

  override fun setRunnerLayoutUi(runnerLayoutUi: RunnerLayoutUi?) {
    delegate.runnerLayoutUi = runnerLayoutUi
  }

  override fun isHiddenContent() = true

  override fun getReusePolicy(): RunContentDescriptorReusePolicy = delegate.reusePolicy

  override fun setReusePolicy(reusePolicy: RunContentDescriptorReusePolicy) {
    delegate.reusePolicy = reusePolicy
  }
}