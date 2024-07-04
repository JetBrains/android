/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.utils.HtmlBuilder

const val DATA_BINDING_MAPPER_CLASS_NAME = "androidx.databinding.DataBinderMapperImpl"

internal object DataBindingErrorUtils {

  /**
   * Checks if the given [throwable] is a [ClassNotFoundException] caused by not being able to load
   * the `androidx.databinding.DataBinderMapperImpl` class.
   *
   * If the given [throwable] is not likely to be caused by this problem, the method will return
   * false.
   */
  @JvmStatic
  fun handleDataBindingMapperError(throwable: Throwable, builder: HtmlBuilder): Boolean {
    if (throwable !is ClassNotFoundException) return false
    if (throwable.message != DATA_BINDING_MAPPER_CLASS_NAME) return false

    builder
      .addItalic(DATA_BINDING_MAPPER_CLASS_NAME)
      .add(" class could not be found.")
      .newline()
      .add(
        "This is likely caused by trying to render a layout using data binding directly from a library module."
      )
      .newline()
      .newline()

    return true
  }
}
