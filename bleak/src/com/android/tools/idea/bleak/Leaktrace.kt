/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.bleak

class Leaktrace(val elements: List<LeaktraceElement>) {
  override fun toString() = elements.joinToString(separator = "\n")

  val size: Int
    get() = elements.size

  // negative indices count backwards from the end (-1 is the last element)
  fun element(index: Int): LeaktraceElement? {
    val realIndex = if (index < 0) elements.size + index else index
    return if (realIndex >= 0 && realIndex < elements.size) elements[realIndex] else null
  }

  fun signatureAt(index: Int): String = element(index)?.signature() ?: ""

  // It's generally more useful in specifying ignore-list patterns to describe a field of a particular
  // class than a field name that has a value of a certain type. In the new format, this description spans
  // elements - the class comes from element 'index', and the field from the subsequent element.
  fun referenceMatches(index: Int, className: String, fieldName: String): Boolean {
    val realIndex = if (index < 0) elements.size - 1 + index else index
    if (realIndex < 0 || realIndex > elements.size - 2) return false
    return elements[realIndex].typeOrClassName() == className && elements[realIndex+1].referenceLabel == fieldName
  }
}

class LeaktraceElement(val type: String, val referenceLabel: String, obj: Any?) {
  val className = if (obj is Class<*>) obj.name else null
  val description = try {
    obj?.let { it.toString().take(100) }
  } catch (t: Throwable) {
    "[EXCEPTION in toString]"
  }

  fun typeOrClassName() = className ?: type

  fun signature() = "$referenceLabel: $type"

  override fun toString() = "${signature()} [$description]"
}
