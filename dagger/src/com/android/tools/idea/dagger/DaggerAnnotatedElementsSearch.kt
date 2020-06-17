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

import com.android.annotations.concurrency.WorkerThread
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.impl.search.AnnotatedElementsSearcher
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.collectDescendantsOfType
import org.jetbrains.kotlin.asJava.ImpreciseResolveResult
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.classes.createTypeFromCanonicalText
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toPsiParameters
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.codegen.ClassBuilderMode
import org.jetbrains.kotlin.codegen.signature.BothSignatureWriter
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.search.PsiBasedClassResolver
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinSourceFilterScope
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

/**
 * Searches for methods, fields and parameters of methods annotated with a given annotation, within a given scope in Java and Kotlin files.
 *
 * It is a replacement for [AnnotatedElementsSearch] that uses [AnnotatedElementsSearcher] and [KotlinAnnotatedElementsSearcher].
 *
 * [DaggerAnnotatedElementsSearch] uses [AnnotatedElementsSearcher] for JAVA.
 * [DaggerAnnotatedElementsSearch] works directly with [KotlinAnnotationsIndex] for Kotlin. The main goal is to minimizes calls of
 * [KtElement.analyze]. [KtElement.analyze] is the main bottleneck in [KotlinAnnotatedElementsSearcher].
 * It allows to speed up computation up to 30-40 times for large projects.
 */
@Service
class DaggerAnnotatedElementsSearch(private val project: Project) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.getService(DaggerAnnotatedElementsSearch::class.java)!!
  }

  fun getAnnotation(annotationFQN: String): PsiClass? {
    return JavaPsiFacade.getInstance(project).findClass(annotationFQN, GlobalSearchScope.allScope(project))
  }

  private fun search(
    annotationFQN: String,
    scope: SearchScope,
    kotlinProcessor: (KtDeclaration) -> Unit,
    javaClassToSearch: Class<out PsiModifierListOwner>,
    javaProcessor: (PsiElement) -> Unit
  ) {
    val annotationClass = getAnnotation(annotationFQN) ?: return
    val psiBasedClassResolver = PsiBasedClassResolver.getInstance(annotationClass)
    val candidates = getKotlinAnnotationCandidates(annotationClass, scope)
    candidates.filterIsInstance<KtAnnotationEntry>().forEach { annotation ->
      // For regular import.
      if (psiBasedClassResolver.matchAnnotation(annotation) ||
          // Fot import via alias, i.e. import example.Annotation as alias.
          (annotation.shortName?.asString() != annotationClass.name &&
           annotation.containingKtFile.findImportByAlias(annotation.shortName.toString())?.importedFqName?.asString() == annotationFQN) ||
          // For usage as FQCN, i.e @example.Annotation val field... .
          annotation.typeReference?.typeElement?.textMatches(annotationFQN) == true) {

        val declaration = PsiTreeUtil.getParentOfType<KtDeclaration>(annotation, KtDeclaration::class.java) ?: return@forEach
        kotlinProcessor(declaration)
      }
    }

    AnnotatedElementsSearcher().execute(AnnotatedElementsSearch.Parameters(annotationClass, scope, javaClassToSearch)) {
      javaProcessor(it)
      true
    }
  }

  fun searchClasses(annotationFQN: String, scope: SearchScope): Collection<PsiClass> {
    val result = mutableListOf<PsiClass>()

    val kotlinProcessor: (KtDeclaration) -> Unit = kotlinProcessor@{ declaration ->
      if (declaration is KtClassOrObject && declaration !is KtEnumEntry) {
        val psiClass = declaration.toLightClass() ?: return@kotlinProcessor
        result.add(psiClass)
      }
    }

    val javaProcessor: (PsiElement) -> Unit = {
      if (it is PsiClass) {
        result.add(it)
      }
    }

    search(annotationFQN, scope, kotlinProcessor, PsiClass::class.java, javaProcessor)
    return result
  }

  fun searchParameters(annotationFQN: String, scope: SearchScope, unboxedPsiType: PsiType): Collection<PsiParameter> {
    val result = mutableListOf<PsiParameter>()

    val kotlinProcessor: (KtDeclaration) -> Unit = kotlinProcessor@{ declaration ->
      val parameter = declaration as? KtParameter ?: return@kotlinProcessor
      if (parameter.typeReference?.equalsToPsiType(unboxedPsiType) == true) {
        val param = parameter.toPsiParameters().firstOrNull() ?: return@kotlinProcessor
        result.add(param)
      }
    }

    val javaProcessor: (PsiElement) -> Unit = {
      if (it is PsiParameter && it.type.unboxed == unboxedPsiType) {
        result.add(it)
      }
    }

    search(annotationFQN, scope, kotlinProcessor, PsiParameter::class.java, javaProcessor)
    return result
  }

  fun searchMethods(annotationFQN: String, scope: SearchScope, unboxedPsiType: PsiType): Collection<PsiMethod> {
    val result = mutableListOf<PsiMethod>()

    val kotlinProcessor: (KtDeclaration) -> Unit = kotlinProcessor@{ declaration ->
      val func = declaration as? KtNamedFunction ?: return@kotlinProcessor
      if (func.typeReference?.equalsToPsiType(unboxedPsiType) == true) {
        val method = LightClassUtil.getLightClassMethod(func) ?: return@kotlinProcessor
        result.add(method)
      }
    }

    val javaProcessor: (PsiElement) -> Unit = {
      if (it is PsiMethod && it.returnType?.unboxed == unboxedPsiType) {
        result.add(it)
      }
    }

    search(annotationFQN, scope, kotlinProcessor, PsiMethod::class.java, javaProcessor)
    return result
  }

  fun searchFields(annotationFQN: String, scope: SearchScope, unboxedPsiType: PsiType): Collection<PsiField> {
    val result = mutableListOf<PsiField>()

    val kotlinProcessor: (KtDeclaration) -> Unit = kotlinProcessor@{ declaration ->
      val property = declaration as? KtProperty ?: return@kotlinProcessor
      if (property.typeReference?.equalsToPsiType(unboxedPsiType) == true) {
        val field = LightClassUtil.getLightClassBackingField(property) ?: return@kotlinProcessor
        result.add(field)
      }
    }

    val javaProcessor: (PsiElement) -> Unit = {
      if (it is PsiField && it.type.unboxed == unboxedPsiType) {
        result.add(it)
      }
    }

    search(annotationFQN, scope, kotlinProcessor, PsiField::class.java, javaProcessor)
    return result
  }

  fun searchParameterOfMethodAnnotatedWith(
    annotationFQN: String,
    scope: SearchScope,
    unboxedPsiType: PsiType
  ): Collection<PsiParameter> {
    val result = mutableListOf<PsiParameter>()

    val kotlinProcessor: (KtDeclaration) -> Unit = kotlinProcessor@{ declaration ->
      val ktFunction = declaration as? KtFunction ?: return@kotlinProcessor
      ktFunction.valueParameters.forEach {
        if (it.typeReference?.equalsToPsiType(unboxedPsiType) == true) {
          val param = it.toPsiParameters().firstOrNull() ?: return@kotlinProcessor
          result.add(param)
        }
      }
    }

    val javaProcessor: (PsiElement) -> Unit = { method ->
      if (method is PsiMethod) {
        result.addAll(method.parameterList.parameters.filter { it.type.unboxed == unboxedPsiType })
      }
    }

    search(annotationFQN, scope, kotlinProcessor, PsiMethod::class.java, javaProcessor)
    return result
  }

  // Copied from KotlinAnnotatedElementsSearcher#getKotlinAnnotationCandidates
  private fun getKotlinAnnotationCandidates(annClass: PsiClass, useScope: SearchScope): Collection<PsiElement> {
    if (useScope is GlobalSearchScope) {
      val name = annClass.name ?: return emptyList()
      val scope = KotlinSourceFilterScope.sourcesAndLibraries(useScope, annClass.project)
      return KotlinAnnotationsIndex.getInstance().get(name, annClass.project, scope)
    }

    return (useScope as LocalSearchScope).scope.flatMap { it.collectDescendantsOfType<KtAnnotationEntry>() }
  }
}

/**
 * Returns true if KtTypeReference corresponds to the a given PsiType.
 *
 * This method aim to minimise number of calls for KtTypeReference.analyze(BodyResolveMode.PARTIAL).
 * KtTypeReference.analyze(BodyResolveMode.PARTIAL) is slow.
 * It allows to speed up DaggerAnnotatedElementsSearch up to 6 times.
 */
@WorkerThread
private fun KtTypeReference.equalsToPsiType(unboxedPsiType: PsiType): Boolean {
  val notNullableTypeElement = if (typeElement is KtNullableType) (typeElement as KtNullableType).innerType else typeElement

  if (notNullableTypeElement !is KtUserType) return false
  val shortName = notNullableTypeElement.referencedName ?: return false
  if (unboxedPsiType is PsiClassType) {
    if (shortName != unboxedPsiType.name &&
        containingKtFile.findImportByAlias(shortName)?.importedFqName?.shortName()?.asString() != unboxedPsiType.name) {
      return false
    }
  }
  if (unboxedPsiType is PsiPrimitiveType) {
    if (!KotlinBuiltIns.FQ_NAMES.primitiveTypeShortNames.contains(Name.identifier(shortName))) {
      return false
    }
  }
  ProgressManager.checkCanceled()
  val kotlinType = analyze(BodyResolveMode.PARTIAL).get(BindingContext.TYPE, this) ?: return false
  val psiType = kotlinType.toPsi(this)
  return psiType == unboxedPsiType
}

private fun KotlinType.toPsi(context: PsiElement): PsiType? {
  val notNullableType = makeNotNullable()
  if (KotlinBuiltIns.isPrimitiveType(notNullableType) ||
      KotlinBuiltIns.isPrimitiveArray(notNullableType)) {
    return kotlinTypeToPrimitive[notNullableType.fqName?.asString()]
  }

  // Extracted from ultraLightUtils.kt#KtUltraLightSupport.mapType
  val signatureWriter = BothSignatureWriter(BothSignatureWriter.Mode.SKIP_CHECKS)
  kotlinTypeMapper.mapType(notNullableType, signatureWriter)
  return createTypeFromCanonicalText(signatureWriter.toString(), context)
}

// Inspired by KtLightAnnotationsValues.kt#psiType
private val kotlinTypeToPrimitive: Map<String, PsiType> = mapOf(
  "kotlin.Int" to PsiType.INT,
  "kotlin.Long" to PsiType.LONG,
  "kotlin.Short" to PsiType.SHORT,
  "kotlin.Boolean" to PsiType.BOOLEAN,
  "kotlin.Byte" to PsiType.BYTE,
  "kotlin.Char" to PsiType.CHAR,
  "kotlin.Double" to PsiType.DOUBLE,
  "kotlin.Float" to PsiType.FLOAT,
  "kotlin.IntArray" to PsiType.INT.createArrayType(),
  "kotlin.LongArray" to PsiType.LONG.createArrayType(),
  "kotlin.ShortArray" to PsiType.SHORT.createArrayType(),
  "kotlin.BooleanArray" to PsiType.BOOLEAN.createArrayType(),
  "kotlin.ByteArray" to PsiType.BYTE.createArrayType(),
  "kotlin.CharArray" to PsiType.CHAR.createArrayType(),
  "kotlin.DoubleArray" to PsiType.DOUBLE.createArrayType(),
  "kotlin.FloatArray" to PsiType.FLOAT.createArrayType()
)

private val kotlinTypeMapper = KotlinTypeMapper(
  BindingContext.EMPTY, ClassBuilderMode.LIGHT_CLASSES,
  JvmProtoBufUtil.DEFAULT_MODULE_NAME, KotlinTypeMapper.LANGUAGE_VERSION_SETTINGS_DEFAULT
)

private fun PsiBasedClassResolver.matchAnnotation(annotationEntry: KtAnnotationEntry): Boolean {
  return annotationEntry.calleeExpression?.constructorReferenceExpression
    ?.let { ref -> canBeTargetReference(ref) } == ImpreciseResolveResult.MATCH
}