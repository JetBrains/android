/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.structure.GradleResolver
import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.PsModelDescriptor
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf

internal fun <T> ResolvedValue<T>.asTestValue(): T? = (this as? ResolvedValue.Set<T>)?.resolved
internal fun <T : Any> Annotated<ParsedValue<T>>.asTestValue(): T? = value.maybeValue
internal fun <T : Any> T.asParsed(): ParsedValue<T> = ParsedValue.Set.Parsed(this, DslText.Literal)
internal fun <T : Any> Pair<String, T>.asParsed() = ParsedValue.Set.Parsed(dslText = DslText.Reference(first), value = second)
internal fun <T : Any> Annotated<ParsedValue<T>>.asUnparsedValue(): String? =
  ((value as? ParsedValue.Set.Parsed<T>)?.dslText as? DslText.OtherUnparsedDslText)?.text
internal val <T : Any> Annotated<PropertyValue<T>>.resolved get() = value.resolved
internal val <T : Any> Annotated<PropertyValue<T>>.parsedValue get() = value.parsedValue

fun PsModelDescriptor.testEnumerateProperties(): Set<ModelProperty<*, *, *, *>> {
  val result = mutableSetOf<ModelProperty<*, *, *, *>>()
  enumerateProperties(receiver = object : PsModelDescriptor.PropertyReceiver {
    override fun <T : PsModel> receive(model: T, property: ModelProperty<T, *, *, *>) {
      result.add(property)
    }
  })
  return result
}

inline fun <reified T : ModelDescriptor<*, *, *>> T.testEnumerateProperties() =
  T::class
    .members
    .mapNotNull { it as? KProperty<*> }
    .filter {
      it.parameters.size == 1 && it.parameters[0].kind == KParameter.Kind.INSTANCE &&
      it.returnType.isSubtypeOf(ModelProperty::class.createType(
        listOf(KTypeProjection.STAR, KTypeProjection.STAR, KTypeProjection.STAR, KTypeProjection.STAR)))
    }
    .map { it.getter.call(this) }.toSet()