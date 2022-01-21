/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.util

import com.android.tools.adtui.TreeWalker
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JCheckBox
import javax.swing.JLabel
import kotlin.test.fail

/**
 * Convenient methods for finding controls in a dialog.
 */
internal fun DialogWrapper.getCheckBox(text: String): JCheckBox =
  TreeWalker(rootPane).descendants().filterIsInstance<JCheckBox>()
    .firstOrNull { it.text == text } ?: fail("Checkbox with label '$text' not found")

internal fun DialogWrapper.getLabel(text: String): JLabel =
  TreeWalker(rootPane).descendants().filterIsInstance<JLabel>()
    .firstOrNull { it.text == text } ?: fail("Label '$text' not found")

internal inline fun <reified T> DialogWrapper.findComponentWithLabel(text: String): T {
  val components = TreeWalker(rootPane).descendants().toList()
  val labelIndex = components.indexOfFirst { it is JLabel && it.text == text }
  if (labelIndex < 0) {
    fail("${T::class.simpleName} with label '$text' not found")
  }
  val component = components[labelIndex + 1]

  return component as? T
         ?: fail("Component with label '$text' is a ${component::class.simpleName} but was expecting a ${T::class.simpleName}")
}
