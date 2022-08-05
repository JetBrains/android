/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.kotlin

/**
 * Returns an [Enum] of which [Enum.name] matches [value] from the given class [E]. If no Enum matches, returns [default] instead.
 */
inline fun <reified E : Enum<E>> enumValueOfOrDefault(value: String, default: E): E = enumValueOfOrNull<E>(value) ?: default

/**
 * Returns an [Enum] of which [Enum.name] matches [value] from the given class [E]. Null if no Enum matches [value].
 */
inline fun <reified E : Enum<E>> enumValueOfOrNull(value: String): E? {
  return try {
    enumValueOf<E>(value)
  }
  catch (_: Exception) {
    null
  }
}