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
  var deprecatedBanner: ServiceDeprecatedBanner? = null
    private set(value) {
      if (field != null) {
        remove(field)
      }
      if (value != null) {
        super<JPanel>.add(value, BorderLayout.NORTH)
      }
      revalidate()
      field = value
    }

  fun setComponent(component: JComponent) {
    val disposables = components.filterIsInstance<Disposable>()
    removeAll()
    if (disposables.isNotEmpty()) {
      disposables.forEach { Disposer.dispose(it) }
    }
    if (component is Disposable) {
      Disposer.register(this, component)
    }
    deprecatedBanner?.let { super<JPanel>.add(it, BorderLayout.NORTH) }
    super.add(component, BorderLayout.CENTER)
  }

  fun addDeprecatedBanner(banner: ServiceDeprecatedBanner, closeAction: () -> Unit) {
    banner.setCloseAction {
      deprecatedBanner = null
      closeAction()
    }
    deprecatedBanner = banner
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
