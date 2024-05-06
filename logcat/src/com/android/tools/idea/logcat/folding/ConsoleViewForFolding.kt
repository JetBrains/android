/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.folding

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.JComponent

private val internalComponent = object : JComponent() {}

/**
 * A noop implementation of [ConsoleView].
 *
 * The sole purpose of this class to allow us to access
 * [com.intellij.execution.ConsoleFolding.isEnabledForConsole] which requires a valid ConsoleView
 * object.
 *
 * This is so that we can ignore non-relevant ConsoleFolding extensions such as
 * GitProgressOutputConsoleFolding etc.
 */
internal class ConsoleViewForFolding : ConsoleView {
  override fun dispose() {}

  override fun getComponent(): JComponent = internalComponent

  override fun getPreferredFocusableComponent(): JComponent = internalComponent

  override fun print(text: String, contentType: ConsoleViewContentType) {}

  override fun clear() {}

  override fun scrollTo(offset: Int) {}

  override fun attachToProcess(processHandler: ProcessHandler) {}

  override fun setOutputPaused(value: Boolean) {}

  override fun isOutputPaused(): Boolean = false

  override fun hasDeferredOutput(): Boolean = false

  override fun performWhenNoDeferredOutput(runnable: Runnable) {}

  override fun setHelpId(helpId: String) {}

  override fun addMessageFilter(filter: Filter) {}

  override fun printHyperlink(hyperlinkText: String, info: HyperlinkInfo?) {}

  override fun getContentSize(): Int = 0

  override fun canPause(): Boolean = canPause()

  override fun createConsoleActions(): Array<AnAction> = emptyArray()

  override fun allowHeavyFilters() {}
}
