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
package com.android.tools.idea.insights.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel

private const val errorMsg = "Please call setComponent to add a component to this panel."

/** A shell for containing the contents of an insights tab. */
class AppInsightsTabPanel : JPanel(BorderLayout()), Disposable {
  fun setComponent(component: JComponent) {
    val disposable = components.firstOrNull() as? Disposable
    removeAll()
    if (disposable != null) {
      Disposer.dispose(disposable)
    }
    if (component is Disposable) {
      Disposer.register(this, component)
    }
    super.add(component)
  }

  override fun dispose() = Unit

  // Start of stubbed overrides section.
  override fun add(comp: Component?): Component = throw UnsupportedOperationException(errorMsg)
  override fun add(name: String?, comp: Component?): Component =
    throw UnsupportedOperationException(errorMsg)
  override fun add(comp: Component?, index: Int): Component =
    throw UnsupportedOperationException(errorMsg)
  override fun add(comp: Component, constraints: Any?) =
    throw UnsupportedOperationException(errorMsg)
  override fun add(comp: Component?, constraints: Any?, index: Int) =
    throw UnsupportedOperationException(errorMsg)
}
