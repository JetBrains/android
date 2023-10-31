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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.Container
import javax.swing.JComponent

/** Class used to wrap and unwrap [component] inside another view. */
class WrapLogic(
  parentDisposable: Disposable,
  private val component: JComponent,
  private val container: Container,
) : Disposable {
  private var newContainer: JComponent? = null

  init {
    Disposer.register(parentDisposable, this)
  }

  fun wrapComponent(wrap: (Disposable, JComponent) -> JComponent) {
    check(newContainer == null) { "Can't wrap, component is already wrapped" }

    container.remove(component)
    newContainer = wrap(this, component)
    container.add(newContainer)
  }

  override fun dispose() {
    try {
      unwrapComponent()
    } catch (_: IllegalStateException) {}
  }

  private fun unwrapComponent() {
    val newContainer = checkNotNull(newContainer) { "Can't unwrap, component is not wrapped" }

    newContainer.remove(component)
    container.remove(newContainer)
    this.newContainer = null

    container.add(component)

    container.invalidate()
    container.repaint()
  }
}
