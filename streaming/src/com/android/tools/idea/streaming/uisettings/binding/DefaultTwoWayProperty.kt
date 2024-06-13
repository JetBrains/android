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

/**
 * Standard implementation of a [TwoWayProperty].
 */
internal open class DefaultTwoWayProperty<T>(initialValue: T) : TwoWayProperty<T> {
  private val listeners = ListenerCollection.createWithDirectExecutor<ChangeListener<T>>()
  private var actualValue = initialValue
  private val emptyChangeListener = ChangeListener<T> {}
  override var uiChangeListener = emptyChangeListener
    set(value) {
      if (field === emptyChangeListener || value === emptyChangeListener) field = value else error("uiChangeListener is already specified")
    }

  override fun clearUiChangeListener() {
    uiChangeListener = emptyChangeListener
  }

  override val value: T
    get() = actualValue

  override fun addControllerListener(listener: ChangeListener<T>) {
    listeners.add(listener)
    listener.valueChanged(value)
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

  override fun <U> createMappedProperty(toTarget: (T) -> U, fromTarget: (U) -> T): TwoWayProperty<U> {
    val property = DefaultTwoWayProperty(toTarget(value))
    property.uiChangeListener = ChangeListener { setFromUi(fromTarget(it)) }
    listeners.add { property.setFromController(toTarget(it)) }
    return property
  }

  override fun and(other: ReadOnlyProperty<Boolean>): ReadOnlyProperty<Boolean> {
    if (actualValue !is Boolean) error("Boolean property required")
    val result = DefaultTwoWayProperty((actualValue as Boolean) and other.value)
    addControllerListener { result.setFromController((it as Boolean) and other.value) }
    other.addControllerListener { result.setFromController((actualValue as Boolean) and it) }
    return result
  }

  override fun not(): ReadOnlyProperty<Boolean> {
    if (actualValue !is Boolean) error("Boolean property required")
    val result = DefaultTwoWayProperty(!(actualValue as Boolean))
    addControllerListener { result.setFromController(!(it as Boolean)) }
    return result
  }
}
