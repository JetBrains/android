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
package com.android.tools.idea.gradle.project.model

import com.android.ide.common.repository.AgpVersion
import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaType

@RunWith(JUnit4::class)
class GradleAndroidModelDataTest {

  @Test
  fun `ensure pure data class`() {
    // GradleAndroidModelDataImpl is supposed to be a pure data class (i.e. no other fields than supporting data class properties).
    validate(GradleAndroidModelDataImpl::class, asInterface = false)
  }

  @Test
  fun `ensure pure interface`() {
    // GradleAndroidModelData is supposed to be a pure interface (i.e. primitive types, enums and interfaces as result types only).
    validate(GradleAndroidModelData::class, asInterface = true)
  }
}

private fun validate(klass: KClass<*>, asInterface: Boolean = false) {
  when (klass) {
    File::class -> return
    else -> Unit
  }
  val constructorProperties = klass.primaryConstructor?.parameters?.map { it.name }?.toSet().orEmpty()
  val allFieldBackedProperties = klass.memberProperties.filter { it.javaField != null }.map { it.name }.toSet()
  assertThat(allFieldBackedProperties - constructorProperties).named("Properties of $klass").isEmpty()

  data class Item(
    val source: Any,
    val propertyKType: KType,
    val propertyRawType: Type,
    val propertyType: Type,
    val fieldType: Type,
    val isFinal: Boolean?
  )

  val itemsToValidate =
    when (asInterface) {
      true -> klass.members
        .map { prop ->
          val propertyKType = prop.returnType
          Item(
            source = prop,
            propertyKType = propertyKType,
            propertyRawType = propertyKType.javaType.maybeRawType(),
            propertyType = propertyKType.javaType,
            fieldType = propertyKType.javaType.maybeRawType(), // Pretend to be backed by a field of the same type.
            isFinal = null
          )
        }
      false -> klass.memberProperties
        .mapNotNull { prop -> prop.javaField?.let { prop to it } }
        .map { (prop, field) ->
          val propertyKType = prop.returnType
          Item(
            source = prop,
            propertyKType = propertyKType,
            propertyRawType = propertyKType.javaType.maybeRawType(),
            propertyType = propertyKType.javaType,
            fieldType = field.type,
            isFinal = prop !is KMutableProperty<*>
          )
        }
    }


  itemsToValidate.forEach { item ->
    with(item) {
      fun unexpected(message: String = "Unexpected type: $propertyKType"): Nothing {
        fail(
          "\n" + """
           ${item.source}
              $message
           """.trimIndent()
        )
        error("")
      }
      when {
        fieldType != propertyRawType -> unexpected("Does not match backing field type: ${fieldType} and $propertyRawType")
        !asInterface && isFinal != true -> unexpected("Property must be final")
      }

      fun KTypeProjection.validateTypeArgument() {
        validate(type?.classifier as? KClass<*> ?: unexpected("Unexpected type argument: {propertyKType.arguments[0]}"))
      }

      when (propertyType) {
        is ParameterizedType -> {
          when (propertyKType.classifier) {
            Collection::class, List::class, Set::class -> {
              propertyKType.arguments[0].validateTypeArgument()
            }
            Map::class -> {
              propertyKType.arguments[0].validateTypeArgument()
              propertyKType.arguments[1].validateTypeArgument()
            }
            else -> unexpected("Unexpected parameterized type")
          }
        }
        is Class<*> -> {
          val kl = propertyKType.classifier as? KClass<*> ?: unexpected("Unexpected classifier: ${propertyKType.classifier}")
          when {
            kl == String::class -> Unit
            kl == File::class -> Unit
            kl == Int::class -> Unit
            kl == Boolean::class -> Unit
            kl == AgpVersion::class && asInterface -> Unit
            propertyType.isEnum -> Unit
            !propertyType.isKotlinClass() -> unexpected("Non-kotlin class is not allowed: $propertyKType")
            !asInterface && !kl.isFinal -> unexpected("Final class is required: $propertyKType")
            kl.isOpen -> unexpected("Open class is not allowed: $propertyKType")
            kl.objectInstance != null -> unexpected("Object is not allowed: $propertyKType")
            !asInterface && propertyType.isInterface -> unexpected("Interface type is not allowed: $propertyKType")
            asInterface && !propertyType.isInterface -> unexpected("Must be primitive, enum or interface type: $propertyKType")
            propertyType.isPrimitive -> unexpected("Primitive type is unexpected: $propertyKType")
            propertyType.isAnnotation -> unexpected("Annotation type is not allowed: $propertyKType")
            propertyType.isAnonymousClass -> unexpected("Anonymous type is not allowed: $propertyKType")
            propertyType.isArray -> unexpected("Array type is not allowed: $propertyKType")
            propertyType.isLocalClass -> unexpected("Local class is not allowed: $propertyKType")
            propertyType.isSynthetic -> unexpected("Synthetic class is not allowed: $propertyKType")
            !asInterface && kl.isData -> validate(kl)
            asInterface && propertyType.isInterface -> validate(kl)
            else -> unexpected()
          }
        }
        else -> unexpected()
      }
    }
  }
}

private fun Type.maybeRawType(): Type {
  return (this as? ParameterizedType)?.rawType ?: this
}
