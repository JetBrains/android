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
 * Support for editing a value.
 *
 * The support includes value validation, and will at some point
 * include support for completions.
 */
interface EditingSupport {

  /**
   * Validation support.
   *
   * Supply a callback for validating a value currently int he editor.
   * The return of the validation is an error category (error / warning)
   * and a message to display to the user.
   */
  val validation: EditingValidation
    get() = { EDITOR_NO_ERROR }

  private class DefaultEditingSupport : EditingSupport

  companion object {
    val INSTANCE: EditingSupport = DefaultEditingSupport()
  }
}

/** Possible error categories for [EditingValidation] lambdas */
enum class EditingErrorCategory(val outline: String?) {
  NONE(null),
  ERROR("error"),
  WARNING("warning");
}

/** A validation method for a text editor */
typealias EditingValidation = (editedValue: String) -> Pair<EditingErrorCategory, String>

@JvmField
val EDITOR_NO_ERROR = Pair(EditingErrorCategory.NONE, "")
