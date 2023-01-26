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
package com.android.tools.idea.nav.safeargs.psi.java

import com.android.tools.idea.nav.safeargs.index.NavArgumentData
import com.android.tools.idea.psi.annotateType
import com.android.tools.idea.psi.light.NullabilityLightFieldBuilder
import com.android.utils.usLocaleCapitalize
import com.android.utils.usLocaleDecapitalize
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.xml.XmlTag
import com.intellij.util.IncorrectOperationException

internal val MODIFIERS_PUBLIC_CONSTRUCTOR = arrayOf(PsiModifier.PUBLIC)
internal val MODIFIERS_PUBLIC_METHOD = arrayOf(PsiModifier.PUBLIC)
internal val MODIFIERS_STATIC_PUBLIC_METHOD = MODIFIERS_PUBLIC_METHOD + arrayOf(PsiModifier.STATIC)

private const val STRING_FQCN = "java.lang.String"
private const val STRING_FQCN_ARRAY = "java.lang.String[]"
private const val INT_ARRAY = "int[]"
private const val FALLBACK_TYPE = STRING_FQCN

private val NAV_TO_JAVA_TYPE_MAP = mapOf(
  "string" to STRING_FQCN,
  "string[]" to STRING_FQCN_ARRAY,
  "integer" to PsiTypes.intType().name,
  "integer[]" to INT_ARRAY,
  "reference" to PsiTypes.intType().name,
  "reference[]" to INT_ARRAY
)

/**
 * Given type strings we pull out of navigation xml files, generate a corresponding [PsiType]
 * for them.
 *
 * @param modulePackage The current package that safe args are being generated into. This will be
 *    used if `typeStr` is specified with a relative path name (i.e. if it starts with '.')
 * @param context The [PsiElement] context we are in when creating this [PsiType] -- this is needed
 *    for IntelliJ machinery.
 * @param typeStr A String of the type we want to create, e.g. "com.example.SomeClass". This value
 *    can start with a '.', e.g. ".util.SomeClass", at which point it will be placed within the
 *    current module package. This value can also be a special type as documented here:
 *    https://developer.android.com/guide/navigation/navigation-pass-data#supported_argument_types
 *    If null, `defaultValue` will be used to infer the type.
 * @param defaultValue The default value specified for this type. This is used as a fallback if
 *    `typeStr` itself is not specified.
 *
 */
internal fun parsePsiType(modulePackage: String, typeStr: String?, defaultValue: String?, context: PsiElement): PsiType {
  val psiTypeStr = getPsiTypeStr(modulePackage, typeStr, defaultValue)
  return try {
    PsiElementFactory.getInstance(context.project).createTypeFromText(psiTypeStr, context)
  }
  catch (e: IncorrectOperationException) {
    PsiElementFactory.getInstance(context.project).createTypeFromText(FALLBACK_TYPE, context)
  }
}

internal fun getPsiTypeStr(modulePackage: String, typeStr: String?, defaultValue: String?): String {
  // When specified as inputs to safe args, inner classes in XML should use the Java syntax (e.g. "Outer$Inner"), but IntelliJ resolves
  // the type using dot syntax ("Outer.Inner")
  var psiTypeStr = typeStr?.replace('$', '.')

  if (psiTypeStr == null) {
    psiTypeStr = guessFromDefaultValue(defaultValue)
  }

  psiTypeStr = psiTypeStr.takeUnless { it.isNullOrEmpty() } ?: FALLBACK_TYPE
  psiTypeStr = NAV_TO_JAVA_TYPE_MAP.getOrDefault(psiTypeStr, psiTypeStr)
  return if (!psiTypeStr.startsWith('.')) psiTypeStr else "$modulePackage$psiTypeStr"
}

private fun guessFromDefaultValue(defaultValue: String?): String? {
  if (defaultValue == null || defaultValue == "@null") {
    return null
  }

  val referenceTypeStr = defaultValue.parseReference()
  if (referenceTypeStr != null) return referenceTypeStr

  val longTypeStr = defaultValue.parseLong()
  if (longTypeStr != null) return longTypeStr

  val intTypeStr = defaultValue.parseInt()
  if (intTypeStr != null) return intTypeStr

  val unsignedIntTypeStr = defaultValue.parseUnsignedInt()
  if (unsignedIntTypeStr != null) return unsignedIntTypeStr

  val floatTypeStr = defaultValue.parseFloat()
  if (floatTypeStr != null) return floatTypeStr

  val booleanTypeStr = defaultValue.parseBoolean()
  if (booleanTypeStr != null) return booleanTypeStr

  return null
}

// @[+][package:]id/resource_name -> package.R.id.resource_name
private val RESOURCE_REGEX = Regex("^@[+]?(.+?:)?(.+?)/(.+)$")

private fun String.parseReference(): String? {
  return RESOURCE_REGEX.matchEntire(this)?.let { "reference" }
}

private fun String.parseLong(): String? {
  if (!endsWith('L')) return null
  return substringBeforeLast('L').toLongOrNull()?.let { PsiTypes.longType().name }
}

private fun String.parseInt(): String? {
  return this.toIntOrNull()?.let { PsiTypes.intType().name }
}

private fun String.parseUnsignedInt(): String? {
  if (!this.startsWith("0x")) return null
  try {
    Integer.parseUnsignedInt(this.substring(2), 16)
    return PsiTypes.intType().name
  }
  catch (ignore: NumberFormatException) {
    return null
  }
}

private fun String.parseFloat(): String? {
  return this.toFloatOrNull()?.let { PsiTypes.floatType().name }
}

private fun String.parseBoolean(): String? {
  if (this == "true" || this == "false") {
    return PsiTypes.booleanType().name
  }

  return null
}

internal fun PsiClass.createConstructor(
  navigationElement: PsiElement? = null,
  modifiers: Array<String> = MODIFIERS_PUBLIC_CONSTRUCTOR
): LightMethodBuilder {
  val fallback = this.navigationElement
  return LightMethodBuilder(this, JavaLanguage.INSTANCE)
    .setConstructor(true)
    .addModifiers(*modifiers).apply {
      this.navigationElement = navigationElement ?: fallback
    }
}

internal fun PsiClass.createField(arg: NavArgumentData, modulePackage: String, xmlTag: XmlTag?): LightFieldBuilder {
  val psiType = parsePsiType(modulePackage, arg.type, arg.defaultValue, this)
  val nonNull = psiType is PsiPrimitiveType || arg.isNonNull()
  return NullabilityLightFieldBuilder(manager, arg.name, psiType, nonNull, PsiModifier.PUBLIC, PsiModifier.FINAL).apply {
    this.navigationElement = xmlTag ?: this.navigationElement
  }
}

/**
 * Annotate the target type with the proper nullability based on the <argument> nullable
 * attribute.
 */
internal fun PsiClass.annotateNullability(psiType: PsiType, isNonNull: Boolean = true): PsiType {
  val nonNull = psiType is PsiPrimitiveType || isNonNull

  return project.annotateType(psiType, nonNull, context)
}

internal fun PsiClass.createMethod(
  name: String,
  navigationElement: PsiElement? = null,
  modifiers: Array<String> = MODIFIERS_PUBLIC_METHOD,
  returnType: PsiType = PsiTypes.voidType()
): LightMethodBuilder {
  return LightMethodBuilder(manager, JavaLanguage.INSTANCE, name)
    .setContainingClass(this)
    .setModifiers(*modifiers)
    .setMethodReturnType(returnType).apply {
      this.navigationElement = navigationElement ?: this@createMethod.navigationElement
    }
}

internal fun String.toCamelCase() = this.toUpperCamelCase().usLocaleDecapitalize()
internal fun String.toUpperCamelCase() = this.split("_").joinToString("") { it.usLocaleCapitalize() }