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
package com.android.tools.idea.streaming.uisettings.binding

import com.android.tools.idea.util.ListenerCollection
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * Standard implementation of a [TwoWayProperty].
 */
internal class DefaultTwoWayProperty<T>(initialValue: T) : TwoWayProperty<T> {
  private val listeners = ListenerCollection.createWithDirectExecutor<ChangeListener<T>>()
  private var actualValue = initialValue

  override var uiChangeListener = ChangeListener<T> {}

  override val value: T
    get() = actualValue

  override fun addControllerListener(disposable: Disposable, listener: ChangeListener<T>) {
    listeners.add(listener)
    Disposer.register(disposable) { removeControllerListener(listener) }
    listener.valueChanged(value)
  }

  override fun removeControllerListener(listener: ChangeListener<T>) {
    listeners.remove(listener)
  }

  override fun setFromUi(newValue: T) {
    if (newValue != actualValue) {
      actualValue = newValue
      uiChangeListener.valueChanged(newValue)
    }
  }

  override fun setFromController(newValue: T) {
    actualValue = newValue
    listeners.forEach { it.valueChanged(newValue) }
  }
}
