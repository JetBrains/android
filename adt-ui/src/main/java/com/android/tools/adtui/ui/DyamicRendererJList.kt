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
package com.android.tools.adtui.ui

import com.intellij.ui.components.JBList
import javax.swing.ListModel
import javax.swing.plaf.basic.BasicListUI

/**
 * Creates a JList with dynamically sized rows calculated by the list cell renderer.
 *
 * By default, row heights are fixed and the ones provided by the custom list renderer are not taken
 * into account.
 */
class DynamicRendererList<T> private constructor(model: ListModel<T>, private val listUi: DynamicRendererListUi) : JBList<T>(model) {
  init {
    setUI(listUi)
  }

  override fun repaint(tm: Long, x: Int, y: Int, width: Int, height: Int) {
    @Suppress("UNNECESSARY_SAFE_CALL") // listUi is null until object finishes constructing
    listUi?.triggerUpdate()
    super.repaint(tm, x, y, width, height)
  }

  private class DynamicRendererListUi : BasicListUI() {
    fun triggerUpdate() {
      if (list == null) return
      updateLayoutState()
    }
  }

  companion object {
    fun <T> createDynamicRendererList(model: ListModel<T>): JBList<T> {
      return DynamicRendererList(model, DynamicRendererListUi())
    }
  }
}
