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
package com.android.tools.idea.gradle.dsl.parser

import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import java.util.LinkedList

fun mapToProperties(map: Map<String, Any>, dslFile: GradleDslFile) {
  fun populateFactoryAttributes(key: String, value: Any, factory: GradleDslMethodCall) {
    when (value) {
      is String, is Int, is Boolean -> factory.addNewArgument(factory.createLiteral(value))

      is Factory<*> -> {
        val newFactory = GradleDslMethodCall(factory, GradleNameElement.empty(), key)
        value.forEachIndexed { i, v -> populateFactoryAttributes(i.toString(), v as Any, newFactory) }
        factory.addNewArgument(newFactory)
      }

      is Map<*, *> -> {
        value.forEach { (k, v) -> populateFactoryAttributes(k as String, v as Any, factory) }
      }
    }
  }
  fun GradlePropertiesDslElement.createAssignment(key: String, value: Any) {
    val literal = setNewLiteral(key, value)
    literal.externalSyntax = ASSIGNMENT
  }

  fun populate(key: String, value: Any, element: GradlePropertiesDslElement) {
    when (value) {
      is String -> element.createAssignment(key, value)
      is Int -> element.createAssignment(key, value)
      is Boolean -> element.createAssignment(key, value)
      is Block<*, *> -> {
        val block = TestBlockElement(element, key)
        value.forEach { (k, v) -> populate(k as String, v as Any, block) }
        element.setNewElement(block)
      }

      is Factory<*> -> {
        val factory = GradleDslMethodCall(element, GradleNameElement.empty(), key)
        value.forEachIndexed { i, v -> populateFactoryAttributes(i.toString(), v as Any, factory) }
        element.setNewElement(factory)
      }
      is List<*> -> {
        val dslList = GradleDslExpressionList(element, GradleNameElement.create(key), true)
        value.forEachIndexed { i, v -> populate(i.toString(), v as Any, dslList) }
        element.setNewElement(dslList)
      }

      is Map<*, *> -> {
        val dslMap = GradleDslExpressionMap(element, GradleNameElement.create(key))
        value.forEach { (k, v) -> populate(k as String, v as Any, dslMap) }
        element.setNewElement(dslMap)
      }
    }
  }
  map.forEach { (k, v) -> populate(k, v, dslFile) }
}

private fun GradleDslElement.createLiteral(value: Any): GradleDslLiteral {
  val literal = GradleDslLiteral(this, GradleNameElement.empty())
  literal.setValue(value)
  return literal
}

class TestBlockElement(parent: GradleDslElement, name: String) : GradleDslBlockElement(parent, GradleNameElement.create(name))

class Block<K, V> : HashMap<K, V>()

fun <K, V> blockOf(vararg pairs: Pair<K, V>): Map<K, V> {
  val b = Block<K, V>()
  pairs.toMap(b)
  return b
}

fun blockOf(): Map<*, *> {
  return Block<Any,Any>()
}
class Factory<T> : LinkedList<T>()

fun <T> factoryOf(vararg elements: T): List<T> {
  val b = Factory<T>()
  b.addAll(elements)
  return b
}