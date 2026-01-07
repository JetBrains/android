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

/**
 * Class used to wrap and unwrap [content] inside another component. When unwrapped, [container] is
 * the parent of [content]. When wrapped, [container] is the parent of the wrapper and the wrapper
 * contains [content].
 *
 * If wrapped, [content] is unwrapped on disposal.
 */
class WrapLogic(
  parentDisposable: Disposable,
  private val content: JComponent,
  private val container: Container,
) : Disposable {
  private var newContainer: JComponent? = null

  init {
    Disposer.register(parentDisposable, this)
  }

  /**
   * Wraps [content] into a new container.
   *
   * @param wrap A function that takes [content] and wraps it into a new container. Returns a new
   *   [JComponent] that contains [content].
   */
  fun wrapContent(wrap: (Disposable, JComponent) -> JComponent) {
    check(newContainer == null) { "Can't wrap, content is already wrapped" }

    container.remove(content)
    newContainer = wrap(this, content)
    container.add(newContainer)
  }

  override fun dispose() {
    try {
      unwrapContent()
    } catch (_: IllegalStateException) {}
  }

  private fun unwrapContent() {
    val newContainer = checkNotNull(newContainer) { "Can't unwrap, content is not wrapped" }

    newContainer.remove(content)
    container.remove(newContainer)
    this.newContainer = null

    container.add(content)

    container.invalidate()
    container.repaint()
  }
}
