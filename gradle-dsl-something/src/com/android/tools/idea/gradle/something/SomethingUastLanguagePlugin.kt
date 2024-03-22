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
package com.android.tools.idea.gradle.something

import com.android.tools.idea.gradle.something.psi.SomethingAssignment
import com.android.tools.idea.gradle.something.psi.SomethingBlock
import com.android.tools.idea.gradle.something.psi.SomethingBlockGroup
import com.android.tools.idea.gradle.something.psi.SomethingElement
import com.android.tools.idea.gradle.something.psi.SomethingEntry
import com.android.tools.idea.gradle.something.psi.SomethingFactory
import com.android.tools.idea.gradle.something.psi.SomethingFile
import com.android.tools.idea.gradle.something.psi.SomethingIdentifier
import com.android.tools.idea.gradle.something.psi.SomethingLiteral
import com.android.tools.idea.gradle.something.psi.SomethingProperty
import com.android.tools.idea.gradle.something.psi.SomethingValue
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.parents
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UComment
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.UastQualifiedExpressionAccessType
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.util.ClassSet
import org.jetbrains.uast.util.classSetOf
import org.jetbrains.uast.visitor.UastVisitor
import org.jetbrains.uast.withMargin

class SomethingUastLanguagePlugin : UastLanguagePlugin {

  override val language: Language = SomethingLanguage.INSTANCE
  override val priority: Int = 42 // arbitrary

  override fun getPossiblePsiSourceTypes(vararg uastTypes: Class<out UElement>): ClassSet<PsiElement> =
    classSetOf(SomethingElement::class.java)

  override fun convertElement(element: PsiElement, parent: UElement?, requiredType: Class<out UElement>?): UElement? =
    convertElementWithParentProvider(element, { parent }, requiredType)

  override fun convertElementWithParent(element: PsiElement, requiredType: Class<out UElement>?): UElement? =
    convertElementWithParentProvider(element, { makeUParent(element) }, requiredType)

  private fun makeUParent(element: PsiElement) =
    element.parents(false).mapNotNull { convertElementWithParent(it, null) }.firstOrNull()

  private fun convertElementWithParentProvider(element: PsiElement, parentProvider: () -> UElement?, requiredType: Class<out UElement>?): UElement? {
    return when (element) {
      is SomethingFile -> SomethingUFile(element, this)
      is SomethingAssignment -> SomethingUAssignment(element, parentProvider)
      is SomethingBlock -> SomethingUBlock(element, parentProvider)
      is SomethingFactory -> SomethingUFactory(element, parentProvider)
      else -> null
    }
      ?.takeIf { requiredType?.isAssignableFrom(it.javaClass) ?: true }
  }

  override fun getConstructorCallExpression(element: PsiElement, fqName: String): UastLanguagePlugin.ResolvedConstructor? = null

  override fun getMethodCallExpression(
    element: PsiElement,
    containingClassFqName: String?,
    methodName: String
  ): UastLanguagePlugin.ResolvedMethod? = null

  override fun isExpressionValueUsed(element: UExpression) = true

  override fun isFileSupported(fileName: String) = fileName.endsWith(".gradle.something")
}

class SomethingUFile(override val sourcePsi: SomethingFile, override val languagePlugin: SomethingUastLanguagePlugin) : UFile {
  override val allCommentsInFile: List<UComment> = listOf()
  override val classes: List<UClass> = listOf()
  override val imports: List<UImportStatement> = listOf()
  override val packageName: String = "arbitrary"
  override val psi: PsiFile = sourcePsi
  override val uAnnotations: List<UAnnotation> = listOf()
  val uEntries: List<SomethingUEntry> =
    sourcePsi
      .getEntries()
      .mapNotNull { languagePlugin.convertElement(it, this, SomethingUEntry::class.java) as? SomethingUEntry }

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitFile(this)) return
    uEntries.acceptList(visitor)
    visitor.afterVisitFile(this)
  }
}

interface SomethingUEntry : UExpression

class SomethingUFactory(override val sourcePsi: SomethingFactory, parentProvider: () -> UElement?) : SomethingUEntry, UCallExpression {
  override val classReference: UReferenceExpression? = null
  override val kind: UastCallKind = UastCallKind.METHOD_CALL
  override val methodIdentifier: UIdentifier? = UIdentifier(sourcePsi.identifier, this)
  override val methodName: String? = sourcePsi.identifier.name
  override val psi: PsiElement? = sourcePsi
  override val receiver: UExpression? = null
  override val receiverType: PsiType? = null
  override val returnType: PsiType? = null
  override val typeArgumentCount: Int = 0
  override val typeArguments: List<PsiType> = listOf()
  override val uAnnotations: List<UAnnotation> = listOf()
  override val uastParent: UElement? by lazy(parentProvider)
  override val valueArguments: List<UExpression> = sourcePsi.argumentsList?.arguments?.mapNotNull { it.toSomethingUExpression(this) } ?: listOf()
  override val valueArgumentCount: Int = valueArguments.size
  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)
  override fun resolve(): PsiMethod? = null
}

class SomethingUBlock(override val sourcePsi: SomethingBlock, parentProvider: () -> UElement?) : SomethingUEntry, UCallExpression {
  override val classReference: UReferenceExpression? = null
  override val kind: UastCallKind = UastCallKind.METHOD_CALL
  override val methodIdentifier: UIdentifier? = (sourcePsi.identifier ?: sourcePsi.factory?.identifier)?.let { UIdentifier(it, this) }
  override val methodName: String? = (sourcePsi.identifier ?: sourcePsi.factory?.identifier)?.name
  override val receiver: UExpression? = null
  override val receiverType: PsiType? = null
  override val returnType: PsiType? = null
  override val typeArgumentCount: Int = 0
  override val typeArguments: List<PsiType> = listOf()
  override val uAnnotations: List<UAnnotation> = listOf()
  override val uastParent by lazy(parentProvider)
  override val psi: PsiElement
    get() = sourcePsi
  override val valueArguments: List<UExpression> =
    (sourcePsi.factory?.argumentsList?.arguments?.mapNotNull { it.toSomethingUExpression(this) } ?: listOf()) +
    SomethingULambda(sourcePsi.blockGroup, this)
  override val valueArgumentCount: Int = valueArguments.size
  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override fun resolve(): PsiMethod? = null
  override fun asRenderString(): String {
    val name = methodName ?: methodIdentifier?.name ?: "<noref>"
    val argumentString = valueArguments.dropLast(1).takeIf { it.isNotEmpty() }
      ?.joinToString(prefix = "(", postfix = ")") ?: ""
    val lambdaString = valueArguments.last().asRenderString()
    return "$name$argumentString $lambdaString"
  }
}

class SomethingULambda(override val sourcePsi: SomethingBlockGroup, override val uastParent: UElement?) : ULambdaExpression {
  override val body = SomethingUAction(sourcePsi.entries, this)
  override val functionalInterfaceType: PsiType? = null
  override val psi: PsiElement? = sourcePsi
  override val uAnnotations: List<UAnnotation> = listOf()
  override val valueParameters: List<UParameter> = listOf()

  override fun asRenderString(): String {
    val expressions = body.expressions.joinToString("\n") { it.asRenderString().withMargin }
    return "{\n$expressions\n}"
  }
}

class SomethingUAction(entries: List<SomethingEntry>, override val uastParent: UElement?) : UBlockExpression {
  override val expressions: List<UExpression> =
    entries.mapNotNull { UastFacade.convertElement(it, this, SomethingUEntry::class.java) as? SomethingUEntry }
  override val psi: PsiElement? = null
  override val uAnnotations: List<UAnnotation> = listOf()
}

class SomethingUAssignment(override val sourcePsi: SomethingAssignment, parentProvider: () -> UElement?) : SomethingUEntry, UBinaryExpression {
  override val leftOperand: UExpression = SomethingUSimpleProperty(sourcePsi.identifier, this)
  override val operator: UastBinaryOperator = UastBinaryOperator.ASSIGN
  override val operatorIdentifier: UIdentifier? = null
  override val rightOperand: UExpression = sourcePsi.value?.toSomethingUExpression(this) ?: UastEmptyExpression(this)

  override val uAnnotations: List<UAnnotation> = listOf()
  override fun resolveOperator(): PsiMethod? = null
  override val psi: PsiElement
    get() = sourcePsi
  override val uastParent by lazy(parentProvider)
}

fun SomethingValue.toSomethingUExpression(uastParent: UElement?): UExpression =
  when (this) {
    is SomethingLiteral -> SomethingULiteral(this, uastParent)
    is SomethingFactory -> SomethingUFactory(this) { uastParent }
    is SomethingProperty -> receiver?.let { SomethingUQualifiedProperty(this, uastParent, it) } ?: SomethingUSimpleProperty(this.field, uastParent)
    else -> error("Unexpected SomethingValue: $this")
  }

class SomethingULiteral(override val sourcePsi: SomethingLiteral, override val uastParent: UElement?): ULiteralExpression {
  override val psi: PsiElement? = sourcePsi
  override val uAnnotations: List<UAnnotation> = listOf()
  override val value: Any? = sourcePsi.value
  override fun evaluate(): Any? = value
}

class SomethingUQualifiedProperty(override val sourcePsi: SomethingProperty, override val uastParent: UElement?, receiver: SomethingProperty): UQualifiedReferenceExpression {
  override val accessType: UastQualifiedExpressionAccessType = UastQualifiedExpressionAccessType.SIMPLE
  override val psi: PsiElement? = sourcePsi
  override val receiver: UExpression = receiver.toSomethingUExpression(this)
  override val resolvedName: String? = null
  override val selector: UExpression = SomethingUSimpleProperty(sourcePsi.field, this)
  override val uAnnotations: List<UAnnotation> = listOf()
  override fun resolve(): PsiElement? = null
}

class SomethingUSimpleProperty(override val sourcePsi: SomethingIdentifier, override val uastParent: UElement?): USimpleNameReferenceExpression {
  override val identifier: String = sourcePsi.name ?: "<noref>"
  override val psi: PsiElement? = sourcePsi
  override val resolvedName: String? = null
  override val uAnnotations: List<UAnnotation> = listOf()
  override fun resolve(): PsiElement? = null
}