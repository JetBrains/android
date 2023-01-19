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
package com.android.tools.idea.gradle.project.sync.internal

import com.google.common.base.CaseFormat
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaGetter

/**
 * A helper which given a class extracts and holds the list of Kotlin properties and Java property like getters.
 */
class ModelClassDumperDescriptor(klass: KClass<Any>) {
  class Property(
    /**
     * The name of the property which is either the name of a Kotlin property or a name derived from a Java getter method name.
     */
    val name: String,

    /**
     * A getter to fetch the value of the property.
     */
    val getter: (Any) -> Any?
  )

  private val allFunctions =
    klass.memberFunctions.filter {
      it.visibility == KVisibility.PUBLIC && it.parameters.size == 1
    }.mapNotNull { function ->
      maybeMapJavaGetterToKotlinProperty(function)?.let { name -> name to function }
    }
      .toMap()

  private val allProperties = klass.memberProperties

  private val allNamedProperties: List<Property> = allProperties.mapNotNull { property ->
    when {
      property.visibility == KVisibility.PUBLIC ->
        Property(property.name, property.apply { isAccessible = true }::get)
      property.visibility != KVisibility.PUBLIC -> allFunctions[property.name]?.let { function ->
        Property(property.name, function.apply { isAccessible = true }::call)
      }
      else -> null
    }
  }

  /**
   * Return a property which is can be used to name instances of the class described by this object.
   */
  val displayNameProperty: Property? =
    allNamedProperties.singleOrNull { it.name == "path" }.takeIf { klass == File::class }
      ?: allNamedProperties.singleOrNull { it.name == "displayName" }
      ?: allNamedProperties.singleOrNull { it.name == "id" }
      ?: allNamedProperties.singleOrNull { it.name == "name" }
      ?: allNamedProperties.singleOrNull { it.name.endsWith("Id") }
      ?: allNamedProperties.singleOrNull { it.name.endsWith("Name") }

  /**
   * The list of all properties that should be included in model dumps except [displayNameProperty].
   */
  val namedProperties: List<Property> = allNamedProperties.filter { it.name != displayNameProperty?.name }

  companion object {
    private fun String.toCamelCase(): String = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, this)

    fun maybeMapJavaGetterToKotlinProperty(property: KProperty1<*, *>): String? {
      if (property.javaGetter != null) return null
      val name = property.name
      return when {
        name.startsWith("get") -> name.removePrefix("get").toCamelCase()
        name.startsWith("is") -> name.removePrefix("is").toCamelCase()
        else -> null
      }
    }

    fun maybeMapJavaGetterToKotlinProperty(property: KFunction<*>): String? {
      val name = property.name
      return when {
        name.startsWith("get") -> name.removePrefix("get").toCamelCase()
        name.startsWith("is") -> name.removePrefix("is").toCamelCase()
        else -> null
      }
    }
  }
}