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
package com.android.tools.adtui.categorytable

import com.intellij.util.xmlb.Converter

/** Metadata about an [Attribute] needed to serialize the attribute and its values. */
class AttributeSerializer<C>(
  val name: String,
  val attribute: Attribute<*, C>,
  val converter: Converter<C>,
)

private object StringConverter : Converter<String>() {
  override fun fromString(value: String): String = value

  override fun toString(value: String): String = value
}

inline fun <reified E : Enum<E>> enumConverter() =
  object : Converter<E>() {
    override fun fromString(value: String): E? = runCatching { enumValueOf<E>(value) }.getOrNull()

    override fun toString(value: E): String = value.name
  }

fun Attribute<*, String>.stringSerializer(name: String) =
  AttributeSerializer(name, this, StringConverter)

inline fun <reified E : Enum<E>> Attribute<*, E>.enumSerializer(name: String) =
  AttributeSerializer(name, this, enumConverter<E>())
