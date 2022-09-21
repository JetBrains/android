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
package com.android.build.attribution.proto

import com.android.utils.HelpfulEnumConverter

/**
 * Convert enums using [HelpfulEnumConverter], name-to-name
 *
 * If you need to convert by more than just a name, then feel free to extend this class and its open functions
 */
open class EnumConverter<A : Enum<*>, B : Enum<*>>(
  private val aClass: Class<A>,
  private val bClass: Class<B>
) {
  open fun aToB(a: A): B =
    silent(a, bClass)
  open fun bToA(b: B): A =
    silent(b, aClass)

  private fun <T : Enum<*>, K: Enum<*>> silent(value: T, otherClass: Class<K>) =
    HelpfulEnumConverter(otherClass).convert(value.name) ?: throw IllegalStateException("Class $otherClass does not contain enum with name ${value.name}. Conversion is impossible")
}