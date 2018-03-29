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
package com.android.tools.adtui.model.stdui

/**
 * The Model for a CommonTextField component.
 */
interface CommonTextFieldModel {

  /**
   * The value being edited.
   *
   * It is up to the model to decide when the value should be updated.
   */
  val value: String

  /**
   * Controls enabled/disabled state of the TextField.
   */
  val enabled: Boolean
    get() = true

  /**
   * Controls the editable state of the TextField.
   */
  val editable: Boolean
    get() = true

  /**
   * The place holder value.
   *
   * The value to be displayed when the text editor is empty.
   */
  val placeHolderValue: String
    get() = ""

  /**
   * The current value seen in the TextField.
   *
   * Note: this is updated for every key stroke. It is up to the
   * implementation of this model when [value] should be updated.
   */
  var text: String

  /**
   * Validate the specified string.
   *
   * Return an error message or the empty string if there is no error.
   */
  fun validate(editedValue: String): String = ""

  /**
   * Add a listener for updates to the model.
   */
  fun addListener(listener: ValueChangedListener)

  /**
   * Remove a listener for updates to the model.
   */
  fun removeListener(listener: ValueChangedListener)
}
