/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.avd

sealed class Validation<out T> {
  class Error(val error: String) : Validation<Nothing>()

  class Valid<T>(val value: T) : Validation<T>()

  val errorOrNull: String?
    get() = (this as? Error)?.error

  val valueOrNull: T?
    get() = (this as? Valid)?.value

  companion object {
    /**
     * Validates [value] with [validator], which should return a String in case of error, or null
     * for a valid value.
     */
    fun <T> validate(value: T, validator: (T) -> String?): Validation<T> =
      when (val errorMessage = validator(value)) {
        null -> Valid(value)
        else -> Error(errorMessage)
      }

    /**
     * Validates [value] with [validator], which should return a String in case of error, or null
     * for a valid value. If the value is null, the validator may check this and return an
     * appropriate error message, or a default error message will be used. Regardless, the resulting
     * Validation will contain a non-null type.
     */
    fun <T : Any> validateNotNull(value: T?, validator: (T?) -> String?): Validation<T> =
      when (val result = validate(value, validator)) {
        is Valid ->
          if (result.value == null) Error("Required value is null.") else Valid(result.value)
        is Error -> result
      }
  }
}

inline fun <T, R> Validation<T>.mapValid(mapper: (T) -> R) = valueOrNull?.let { mapper(it) }
