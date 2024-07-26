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
package com.android.tools.idea.gradle.declarative

import com.android.tools.idea.gradle.declarative.ElementType.BLOCK
import com.android.tools.idea.gradle.declarative.ElementType.BOOLEAN
import com.android.tools.idea.gradle.declarative.ElementType.FACTORY
import com.android.tools.idea.gradle.declarative.ElementType.INTEGER
import com.android.tools.idea.gradle.declarative.ElementType.LONG
import com.android.tools.idea.gradle.declarative.ElementType.STRING
import com.android.tools.idea.gradle.declarative.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.declarative.psi.DeclarativeBlock
import com.android.tools.idea.gradle.declarative.psi.DeclarativeFactory
import com.intellij.psi.PsiElement
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef

enum class ElementType(val str: String) {
  STRING("String"),
  INTEGER("Integer"),
  LONG("Long"),
  BOOLEAN("Boolean"),
  BLOCK("Block element"),
  FACTORY("Factory"),
  PROPERTY("Property")
}

fun getType(type: DataTypeRef): ElementType = when (type) {
  is DataTypeRef.Name -> BLOCK
  is DataTypeRef.Type -> when (type.dataType) {
    is DataType.IntDataType -> INTEGER
    is DataType.LongDataType -> LONG
    is DataType.StringDataType -> STRING
    is DataType.BooleanDataType -> BOOLEAN
    else -> ElementType.PROPERTY
  }
}

fun getType(type: DataType): ElementType = when (type) {
  is DataType.IntDataType -> INTEGER
  is DataType.LongDataType -> LONG
  is DataType.StringDataType -> STRING
  is DataType.BooleanDataType -> BOOLEAN
  else -> ElementType.PROPERTY
}

fun PsiElement.getElementType(): ElementType? = when (this) {
  is DeclarativeBlock -> BLOCK
  is DeclarativeFactory -> FACTORY
  is DeclarativeAssignment -> when (this.literal?.value) {
    is String -> STRING
    is Int -> INTEGER
    is Boolean -> BOOLEAN
    is Long -> LONG
    else -> null
  }

  else -> null
}