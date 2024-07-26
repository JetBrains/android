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
package com.android.tools.idea.dagger

import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue
import com.intellij.lang.jvm.annotation.JvmAnnotationClassValue
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import com.intellij.lang.jvm.annotation.JvmNestedAnnotationValue
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtArrayAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtConstantAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtEnumEntryAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtKClassAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.caches.resolve.analyze as analyzeK1
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.constants.AnnotationValue
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.BooleanValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.DoubleValue
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.FloatValue
import org.jetbrains.kotlin.resolve.constants.IntegerValueConstant
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.constants.NullValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

/**
 * Contains a serialized representation of a qualifier annotation.
 *
 * Includes qualifier's fqcn and serialized representation of attribute values.
 *
 * Qualifier is an annotation that has [DaggerClasses.Qualifier] annotation.
 *
 * [fqName]
 * - fq name of qualifier [attributes]
 * - map from attribute name to serialized value
 *
 * We need to store the annotations values in their serialized form because we don't have a common
 * PSI representation between Kotlin and Java PSIs.
 *
 * Serialization convention: enum -> "enum-fqn.enum-fieldName" class -> "class-fqn", if it's a
 * Kotlin fqn map it to a Java fqn annotation -> "annotation-fqcn" primitive Types/String ->
 * "x.toString()" array -> "QualifierInfo.attrValueToString(array[0]),
 * "QualifierInfo.attrValueToString(array[1]) .."
 *
 * @see [serializeAttrValueToString]
 */
internal data class QualifierInfo(val fqName: String, val attributes: Map<String, String>)

private fun isConstantValueSerializableAsString(constant: ConstantValue<*>): Boolean {
  return constant is StringValue ||
    constant is BooleanValue ||
    constant is DoubleValue ||
    constant is FloatValue ||
    constant is IntegerValueConstant<*> ||
    constant is NullValue
}

/**
 * Converts a [ConstantValue] to a [String].
 *
 * [ConstantValue] can be an enum, primitive type or an annotation, String, or Class object. It can
 * also be an array of these types.
 *
 * Returns `null` if we can't reliably serialize value.
 */
private fun serializeAttrValueToString(value: ConstantValue<*>): String? =
  when (value) {
    is ArrayValue ->
      value.value.map { serializeAttrValueToString(it) ?: return null }.joinToString()
    is KClassValue -> {
      val kotlinFqName =
        (value.value as? KClassValue.Value.NormalClass)
          ?.value
          ?.classId
          ?.asSingleFqName()
          ?.asString()
      // Try to map Kotlin fqcn to Java fqcn, e.g kotlin.String -> java.lang.String.
      kotlinFqName?.let {
        JavaToKotlinClassMap.mapKotlinToJava(FqNameUnsafe(it))?.asSingleFqName()?.asString() ?: it
      }
    }
    is AnnotationValue -> value.value.fqName?.asString()
    is EnumValue -> "${value.enumClassId.asSingleFqName().asString()}.${value.enumEntryName}"
    else -> if (isConstantValueSerializableAsString(value)) value.value.toString() else null
  }

/**
 * Converts a [JvmAnnotationAttributeValue] to a [String].
 *
 * [JvmAnnotationAttributeValue] can be an enum, primitive type or an annotation, String, or Class
 * object. It can also be an array of these types.
 *
 * Returns `null` if we can't reliably serialize value.
 */
private fun serializeAttrValueToString(value: JvmAnnotationAttributeValue?): String? =
  when (value) {
    is JvmAnnotationArrayValue ->
      value.values.map { serializeAttrValueToString(it) ?: return null }.joinToString()
    is JvmAnnotationConstantValue -> value.constantValue.toString()
    is JvmAnnotationClassValue -> value.qualifiedName
    is JvmNestedAnnotationValue -> value.value.qualifiedName
    is JvmAnnotationEnumFieldValue -> "${value.containingClassName}.${value.fieldName}"
    else -> null
  }

/**
 * Converts a [KtAnnotationValue] to a [String].
 *
 * Returns `null` if any attr value cannot be serialized.
 */
private fun serializeKtAnnotationValue(value: KtAnnotationValue): String? {
  return when (value) {
    is KtArrayAnnotationValue -> {
      val childValues = value.values.map { serializeKtAnnotationValue(it) ?: return null }
      childValues.joinToString()
    }
    is KtConstantAnnotationValue -> {
      when (value.constantValue) {
        is KaConstantValue.KaStringConstantValue ->
          (value.constantValue as KaConstantValue.KaStringConstantValue).value
        else -> value.constantValue.renderAsKotlinConstant()
      }
    }
    is KtEnumEntryAnnotationValue -> value.callableId?.asSingleFqName()?.asString()
    is KtKClassAnnotationValue.KtLocalKClassAnnotationValue -> value.ktClass.fqName?.asString()
    is KtKClassAnnotationValue.KtNonLocalKClassAnnotationValue ->
      value.classId.normalizeToJVM().asSingleFqName().asString()
    else -> null
  }
}

/**
 * Normalize ClassID by translating to JVM type: kotlin.String -> java.lang.String etc. See
 * [JavaToKotlinClassMap] for the complete mapping.
 *
 * Returns [this] unaltered if it does not map to a JVM type.
 */
private fun ClassId.normalizeToJVM(): ClassId =
  JavaToKotlinClassMap.mapKotlinToJava(asSingleFqName().toUnsafe()) ?: this

/**
 * Returns a [QualifierInfo] for a given [PsiElement] if a qualifier presents and it's only one
 * otherwise returns `null`.
 *
 * Returns `null` if any attr value of Qualifier can't be serialized value. See
 * [serializeAttrValueToString]
 */
internal fun PsiElement.getQualifierInfo(): QualifierInfo? =
  when (this) {
    is KtAnnotated -> this.getQualifierInfoFromKtAnnotated()
    is PsiModifierListOwner -> this.getQualifierInfoFromPsiModifierListOwner()
    else -> null
  }

/** Filters elements that has a [QualifierInfo] that equals to given a [qualifierInfo]. */
internal fun <T : PsiModifierListOwner> Collection<T>.filterByQualifier(
  qualifierInfo: QualifierInfo?
): Collection<T> {
  return this.filter {
    // If it's [KtLightElement], we search for [QualifierInfo] in `kotlinOrigin` of element, e.g
    // QualifierInfo could belong not to field, but to accessor.
    val otherQualifierInfo =
      if (it is KtLightElement<*, *>) (it.kotlinOrigin as? PsiElement)?.getQualifierInfo()
      else it.getQualifierInfo()
    otherQualifierInfo == qualifierInfo
  }
}

private fun KtAnnotated.getQualifierInfoFromKtAnnotated(): QualifierInfo? {
  return if (KotlinPluginModeProvider.isK2Mode()) {
    getQualifierInfoFromKtAnnotatedK2()
  } else {
    getQualifierInfoFromKtAnnotatedK1()
  }
}

private fun KtAnnotated.getQualifierInfoFromKtAnnotatedK1(): QualifierInfo? {
  val annotationDescriptors = annotationEntries.mapNotNull { it.getDescriptor() }
  val qualifiers = annotationDescriptors.filter { it.isQualifier }
  // It is always an error to apply multiple qualifiers. Qualifier is valid only if it's single.
  if (qualifiers.size == 1) {
    val qualifier = qualifiers.single()
    val qualifierFqName = qualifier.fqName?.asString() ?: return null
    val qualifierAttributes =
      qualifier.allValueArguments
        .map {
          it.key.asString() to
            (serializeAttrValueToString(it.value) ?: return@getQualifierInfoFromKtAnnotatedK1 null)
        }
        .toMap()
    return QualifierInfo(qualifierFqName, qualifierAttributes)
  }
  return null
}

@OptIn(KaAllowAnalysisOnEdt::class)
private fun KtAnnotated.getQualifierInfoFromKtAnnotatedK2(): QualifierInfo? {
  allowAnalysisOnEdt {
    analyze(this) {
      val ktDeclarationSymbol =
        (this@getQualifierInfoFromKtAnnotatedK2 as? KtDeclaration)?.symbol ?: return null
      val qualifier =
        ktDeclarationSymbol.annotations.singleOrNull { isQualifier(it.classId) } ?: return null

      val qualifierFqName = qualifier.classId?.asFqNameString() ?: return null
      val qualifierAttributes =
        qualifier.arguments.associate { (attr, arg) ->
          attr.asString() to (serializeKtAnnotationValue(arg) ?: return null)
        }

      return QualifierInfo(qualifierFqName, qualifierAttributes)
    }
  }
}

private fun PsiModifierListOwner.getQualifierInfoFromPsiModifierListOwner(): QualifierInfo? {
  val qualifiers = annotations.filter { it.isQualifier }
  // It is always an error to apply multiple qualifiers. Qualifier is valid only if it's single.
  if (qualifiers.size == 1) {
    val qualifierFqName = qualifiers.first().qualifiedName ?: return null
    val qualifierAttributes =
      qualifiers
        .first()
        .attributes
        .map {
          it.attributeName to
            (serializeAttrValueToString(it.attributeValue)
              ?: return@getQualifierInfoFromPsiModifierListOwner null)
        }
        .toMap()
    return QualifierInfo(qualifierFqName, qualifierAttributes)
  }
  return null
}

private fun KtAnnotationEntry.getDescriptor() =
  analyzeK1(BodyResolveMode.PARTIAL).get(BindingContext.ANNOTATION, this)

private val AnnotationDescriptor.isQualifier: Boolean
  get() = annotationClass?.annotations?.hasAnnotation(DaggerClasses.Qualifier.fqName) == true

private fun KtAnalysisSession.isQualifier(annotationClassId: ClassId?): Boolean =
  annotationClassId
    ?.let { getClassOrObjectSymbolByClassId(it) }
    ?.hasAnnotation(DaggerClasses.Qualifier.classId) == true

private val PsiAnnotation.isQualifier: Boolean
  get() {
    val cls = (this.nameReferenceElement?.resolve() as? PsiClass) ?: return false
    return cls.isAnnotationType && cls.hasAnnotation(DaggerClasses.Qualifier.fqNameString)
  }
