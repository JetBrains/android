/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.property.panel.impl.model.util

import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.impl.ui.EnumValueListCellRenderer
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.ListCellRenderer

class FakeEnumSupport(
  vararg elements: String,
  action: AnAction? = null,
  private val delayed: Boolean = false,
) : EnumSupport {

  override val values: List<EnumValue>
    get() {
      if (delayed) {
        synchronized(lock) {
          lockCount++
          lock.wait(2000L)
        }
      }
      return enumValues
    }

  override val renderer: ListCellRenderer<EnumValue> by lazy { EnumValueListCellRenderer() }

  private val lock = Object()
  private var lockCount = 0
  private val enumValues = mutableListOf<EnumValue>()

  fun releaseAll() {
    synchronized(lock) {
      var attempts = 0
      while (lockCount == 0 && ++attempts < 10) {
        lock.wait(200L)
      }
      if (!delayed || lockCount == 0) {
        error("Nothing to release")
      }
      lock.notifyAll()
    }
  }

  init {
    enumValues.addAll(elements.map { EnumValue.item(it) })
    if (action != null) {
      enumValues.add(EnumValue.action(action))
    }
  }
}
