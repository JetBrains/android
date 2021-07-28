/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.util

// TODO: Migrate to kotlin.collections.firstNotNullOfOrNull once we use Kotlin stdlib 1.5 or higher.
inline fun <T, R : Any> Iterable<T>.firstNotNullResult(transform: (T) -> R?): R? {
  for (element in this) {
    val result = transform(element)
    if (result != null) return result
  }
  return null
}

// TODO: Migrate to kotlin.collections.firstNotNullOfOrNull once we use Kotlin stdlib 1.5 or higher.
inline fun <T, R : Any> Array<T>.firstNotNullResult(transform: (T) -> R?): R? {
  for (element in this) {
    val result = transform(element)
    if (result != null) return result
  }
  return null
}
