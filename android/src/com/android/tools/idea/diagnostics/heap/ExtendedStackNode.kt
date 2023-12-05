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
package com.android.tools.idea.diagnostics.heap

data class ExtendedStackNode(
  val className: String,
  val label: String,
  val isDisposedButReferenced: Boolean,
  val isLoadedWithNominatedLoader: Boolean) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ExtendedStackNode

    if (className != other.className) return false
    if (label != other.label) return false
    if (isDisposedButReferenced != other.isDisposedButReferenced) return false
    if (isLoadedWithNominatedLoader != other.isLoadedWithNominatedLoader) return false

    return true
  }

  override fun hashCode(): Int {
    var result = className.hashCode()
    result = 31 * result + label.hashCode()
    result = 31 * result + isDisposedButReferenced.hashCode()
    result = 31 * result + isLoadedWithNominatedLoader.hashCode()
    return result
  }
}