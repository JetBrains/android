/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw

import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ObservableValue
import com.android.tools.idea.observable.SettableValue
import com.android.tools.idea.observable.expressions.Expression
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.LayoutBuilder
import java.awt.Component
import javax.swing.JLabel
import javax.swing.SwingConstants

fun <T, O> BindingsManager.bindExpression(dest: SettableValue<T>, listenTo: ObservableValue<O>, supplier: () -> T) {
  bind(dest, object: Expression<T>(listenTo) {
    override fun get(): T = supplier()
  })
}

fun invokeLater(modalityState: ModalityState = ModalityState.any(), f: () -> Unit) =
  ApplicationManager.getApplication().invokeLater(f, modalityState)

/**
 * Creates a [JLabel], sets [JLabel.labelFor] and an optional [ContextHelpLabel].
 * It is recommended to create it inside of a cell if context help is used.
 */
fun Cell.labelFor(text: String, forComponent: Component, contextHelpText: String? = null): JLabel {
  val label = if (contextHelpText == null) {
    JBLabel(text)
  }
  else {
    ContextHelpLabel.create(contextHelpText).apply {
      setText(text)
      horizontalTextPosition = SwingConstants.LEFT
    }
  }.apply {
    labelFor = forComponent
  }

  label()
  return label
}

fun LayoutBuilder.verticalGap() {
  row {
    label("")
  }
}
