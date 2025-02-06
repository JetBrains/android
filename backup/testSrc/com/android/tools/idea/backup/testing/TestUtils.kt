/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.idea.backup.testing

import com.android.tools.adtui.TreeWalker
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JButton
import javax.swing.JComponent
import kotlin.test.fail

internal inline fun <reified T> DialogWrapper.findComponent(name: String): T {
  return TreeWalker(rootPane).descendants().filterIsInstance<T>().find {
    (it as JComponent).name == name
  } ?: fail("${T::class.simpleName} named $name was not found")
}

internal fun DialogWrapper.clickOk() {
  val buttons = TreeWalker(this.rootPane).descendants().filterIsInstance<JButton>()
  buttons.first { it.text == "OK" }.doClick()
}

// It's possible to extract the actual text, but it requires knowledge of
// implementation details that might change.
internal fun Presentation.hasTooltip(): Boolean =
  getClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP) != null
