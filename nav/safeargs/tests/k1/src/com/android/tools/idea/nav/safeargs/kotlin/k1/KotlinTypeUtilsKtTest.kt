/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.kotlin.k1

import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.error.ErrorType
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.kotlin.types.typeUtil.nullability
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests on types check, nullabilities check, inferred types check */
@RunsInEdt
class KotlinTypeUtilsKtTest {
  @get:Rule val safeArgsRule = SafeArgsRule(SafeArgsMode.KOTLIN)

  private val builtIn = DefaultBuiltIns.Instance
  private lateinit var moduleDescriptor: ModuleDescriptor

  @Before
  fun setUp() {
    moduleDescriptor = safeArgsRule.module.toDescriptor()!!
  }

  /** Primitive types regular check */
  @Test
  fun checkInteger() {
    val type =
      builtIn.getKotlinType(
        typeStr = "integer",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type).isEqualTo(builtIn.intType)
  }

  @Test
  fun checkIntegerArray() {
    val type =
      builtIn.getKotlinType(
        typeStr = "integer[]",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type)
      .isEqualTo(builtIn.getPrimitiveArrayKotlinTypeByPrimitiveKotlinType(builtIn.intType))
  }

  @Test
  fun checkFloat() {
    val type =
      builtIn.getKotlinType(
        typeStr = "float",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type).isEqualTo(builtIn.floatType)
  }

  @Test
  fun checkFloatArray() {
    val type =
      builtIn.getKotlinType(
        typeStr = "float[]",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type)
      .isEqualTo(builtIn.getPrimitiveArrayKotlinTypeByPrimitiveKotlinType(builtIn.floatType))
  }

  @Test
  fun checkLong() {
    val type =
      builtIn.getKotlinType(
        typeStr = "long",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type).isEqualTo(builtIn.longType)
  }

  @Test
  fun checkLongArray() {
    val type =
      builtIn.getKotlinType(
        typeStr = "long[]",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type)
      .isEqualTo(builtIn.getPrimitiveArrayKotlinTypeByPrimitiveKotlinType(builtIn.longType))
  }

  @Test
  fun checkBoolean() {
    val type =
      builtIn.getKotlinType(
        typeStr = "boolean",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type).isEqualTo(builtIn.booleanType)
  }

  @Test
  fun checkBooleanArray() {
    val type =
      builtIn.getKotlinType(
        typeStr = "boolean[]",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type)
      .isEqualTo(builtIn.getPrimitiveArrayKotlinTypeByPrimitiveKotlinType(builtIn.booleanType))
  }

  /** Not primitive types regular check */
  @Test
  fun checkString() {
    val type =
      builtIn.getKotlinType(
        typeStr = "string",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type).isEqualTo(builtIn.stringType)
  }

  @Test
  fun checkStringArray() {
    val type =
      builtIn.getKotlinType(
        typeStr = "string[]",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type).isEqualTo(builtIn.getArrayType(Variance.INVARIANT, builtIn.stringType))
  }

  @Test
  fun checkResourceReference() {
    val type =
      builtIn.getKotlinType(
        typeStr = "reference",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type).isEqualTo(builtIn.intType)
  }

  @Test
  fun checkCustomType() {
    val type =
      builtIn.getKotlinType(
        typeStr = "test.safeargs.MyCustomType",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      ) as ErrorType

    assertThat(type.nullability()).isEqualTo(TypeNullability.NOT_NULL)
    assertThat(type.debugMessage).isEqualTo("Unresolved type for test.safeargs.MyCustomType")
  }

  @Test
  fun checkCustomTypeArray() {
    val type =
      builtIn.getKotlinType(
        typeStr = "test.safeargs.MyCustomType[]",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
      )

    val elementType = builtIn.getArrayElementType(type) as ErrorType
    assertThat(elementType.debugMessage).isEqualTo("Unresolved type for test.safeargs.MyCustomType")
  }

  /** Inferred types check */
  @Test
  fun checkInferredInteger() {
    val type =
      builtIn.getKotlinType(typeStr = null, defaultValue = "1", moduleDescriptor = moduleDescriptor)
    assertThat(type).isEqualTo(builtIn.intType)
  }

  @Test
  fun checkInferredFloat() {
    val type =
      builtIn.getKotlinType(
        typeStr = null,
        defaultValue = "1f",
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type).isEqualTo(builtIn.floatType)
  }

  @Test
  fun checkInferredLong() {
    val type =
      builtIn.getKotlinType(
        typeStr = null,
        defaultValue = "1L",
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type).isEqualTo(builtIn.longType)
  }

  @Test
  fun checkInferredBoolean() {
    val type =
      builtIn.getKotlinType(
        typeStr = null,
        defaultValue = "true",
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type).isEqualTo(builtIn.booleanType)
  }

  @Test
  fun checkInferredString() {
    val type =
      builtIn.getKotlinType(
        typeStr = null,
        defaultValue = "someString",
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type).isEqualTo(builtIn.stringType)
  }

  @Test
  fun checkInferredNullString() {
    val type =
      builtIn.getKotlinType(
        typeStr = null,
        defaultValue = "@null",
        moduleDescriptor = moduleDescriptor,
      )

    assertThat(type).isEqualTo(builtIn.stringType)
  }

  @Test
  fun checkInferredResourceReference() {
    val type =
      builtIn.getKotlinType(
        typeStr = null,
        defaultValue = "@resourceType/resourceName",
        moduleDescriptor = moduleDescriptor,
      )
    assertThat(type).isEqualTo(builtIn.intType)
  }

  /** Not primitive types nullability check */
  @Test
  fun checkNullableString() {
    val type =
      builtIn.getKotlinType(
        typeStr = "string",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
        isNonNull = false,
      )
    assertThat(type.nullability()).isEqualTo(TypeNullability.NULLABLE)
  }

  @Test
  fun checkNullableCustomType() {
    val type =
      builtIn.getKotlinType(
        typeStr = "test.safeargs.MyCustomType",
        defaultValue = "@null",
        moduleDescriptor = moduleDescriptor,
        isNonNull = false,
      )

    assertThat(type.nullability()).isEqualTo(TypeNullability.NULLABLE)
  }

  @Test
  fun checkNullableStringArray() {
    val type =
      builtIn.getKotlinType(
        typeStr = "string[]",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
        isNonNull = false,
      )
    assertThat(type.nullability()).isEqualTo(TypeNullability.NULLABLE)
  }

  @Test
  fun checkNullableCustomTypeArray() {
    val type =
      builtIn.getKotlinType(
        typeStr = "test.safeargs.MyCustomType[]",
        defaultValue = null,
        moduleDescriptor = moduleDescriptor,
        isNonNull = false,
      )
    assertThat(type.nullability()).isEqualTo(TypeNullability.NULLABLE)
  }
}
