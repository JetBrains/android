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
package com.android.tools.idea.nav.safeargs.psi

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.util.IncorrectOperationException

internal val MODIFIERS_PUBLIC_CONSTRUCTOR = arrayOf(PsiModifier.PUBLIC)
internal val MODIFIERS_PUBLIC_METHOD = arrayOf(PsiModifier.PUBLIC, PsiModifier.FINAL)
internal val MODIFIERS_STATIC_PUBLIC_METHOD = MODIFIERS_PUBLIC_METHOD + arrayOf(PsiModifier.STATIC)

private const val STRING_FQCN = "java.lang.String"
private const val FALLBACK_TYPE = STRING_FQCN

private val NAV_TO_JAVA_TYPE_MAP = mapOf(
  "string" to STRING_FQCN,
  "integer" to "int",
  "reference" to "int"
)

internal fun parsePsiType(modulePackage: String, typeStr: String, context: PsiElement): PsiType {
  @Suppress("NAME_SHADOWING") // Shadowing to convert val to var
  var typeStr = typeStr.takeUnless { it.isEmpty() } ?: FALLBACK_TYPE
  typeStr = NAV_TO_JAVA_TYPE_MAP.getOrDefault(typeStr, typeStr)
  typeStr = if (!typeStr.startsWith('.')) typeStr else "$modulePackage$typeStr"
  return try {
    PsiElementFactory.getInstance(context.project).createTypeFromText(typeStr, context)
  }
  catch (e: IncorrectOperationException) {
    PsiElementFactory.getInstance(context.project).createTypeFromText(FALLBACK_TYPE, context)
  }
}

internal fun PsiClass.createConstructor(modifiers: Array<String> = MODIFIERS_PUBLIC_CONSTRUCTOR): LightMethodBuilder {
  return LightMethodBuilder(this, JavaLanguage.INSTANCE)
    .setConstructor(true)
    .addModifiers(*modifiers)
}

internal fun PsiClass.createMethod(name: String,
                                   modifiers: Array<String> = MODIFIERS_PUBLIC_METHOD,
                                   returnType: PsiType = PsiType.VOID): LightMethodBuilder {
  return LightMethodBuilder(manager, JavaLanguage.INSTANCE, name)
    .setContainingClass(this)
    .setMethodReturnType(returnType)
    .addModifiers(*modifiers)
}
