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
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.util.Disposer
import java.awt.Component
import java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Class used to wrap and unwrap [content] inside another component. When unwrapped, the parent of
 * [content] is the original parent. When wrapped, [content] is a descendant of wrapper. And the
 * parent of wrapper is the original parent of [content].
 *
 * If wrapped, [content] is unwrapped on disposal.
 */
class WrapLogic(parentDisposable: Disposable, private val content: JComponent) : Disposable {
  private var wrapper: JComponent? = null

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
    check(wrapper == null) { "Can't wrap, content is already wrapped" }

    val container = content.parent ?: throw IllegalStateException("Parent can't be null")
    val index = container.components.indexOf(content)
    val focusOwner = content.getContainedFocusOwner()
    container.remove(index)
    wrapper = wrap(this, content)
    assert(SwingUtilities.isDescendingFrom(content, wrapper))
    container.add(wrapper, index)
    focusOwner?.requestFocusInWindow()
  }

  override fun dispose() {
    try {
      unwrapContent()
    } catch (_: IllegalStateException) {}
  }

  private fun unwrapContent() {
    val wrapper = wrapper ?: return

    val container = wrapper.parent ?: throw IllegalStateException("Parent can't be null")
    val index = container.components.indexOf(wrapper)
    val focusOwner = content.getContainedFocusOwner()
    container.remove(index)
    container.add(content, index)
    focusOwner?.requestFocusInWindow()
    this.wrapper = null
  }
}

private fun Component.getContainedFocusOwner(): Component? =
  if (isFocusAncestor()) getCurrentKeyboardFocusManager().focusOwner else null
