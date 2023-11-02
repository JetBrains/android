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

import com.intellij.openapi.Disposable

/**
 * General change listener.
 */
internal fun interface ChangeListener<T> {
  fun valueChanged(newValue: T)
}

/**
 * A property of type [T] that is intended to be used in UI models.
 * A [ReadOnlyProperty] is read but not changed from the UI, however it can be changed from the controller.
 */
internal interface ReadOnlyProperty<T> {
  /**
   * The actual value of this property.
   */
  val value: T

  /**
   * The UI can be notified of changes from the controller by adding a listener.
   * The disposable specified provides auto removal of the listener.
   */
  fun addControllerListener(disposable: Disposable, listener: ChangeListener<T>)

  /**
   * A listener can be removed before the UI is disposed if needed.
   */
  fun removeControllerListener(listener: ChangeListener<T>)

  /**
   * The controller should call this function to specify a new value.
   */
  fun setFromController(newValue: T)
}

/**
 * A property of type [T] that can be changed either from the UI or from the controller.
 * Note: the controller listeners are NOT fired when the property is changed by [setFromUi],
 * and the [uiChangeListener] is NOT fired when the property is changed by [setFromController].
 */
internal interface TwoWayProperty<T> : ReadOnlyProperty<T> {
  /**
   * A controller should supply a [uiChangeListener] if changes from the UI are expected.
   */
  var uiChangeListener: ChangeListener<T>

  /**
   * The UI should call this function to specify a new value.
   */
  fun setFromUi(newValue: T)
}
