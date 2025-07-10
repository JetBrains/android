/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.lang.proguardR8.inspections

import com.android.tools.idea.lang.proguardR8.psi.ProguardR8AnnotationName
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8ClassMemberName
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8ClassName
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8ClassType
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8Field
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8FieldsSpecification
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8JavaRule
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8MethodSpecification
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8QualifiedName
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8RuleWithClassSpecification
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8Visitor
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.childrenOfType


/**
 * The maximum number of classes that are allowed to be affected by a proguard rule.
 */
private const val CLASSES_AFFECTED_LIMIT = 100

/**
 * The inspection description when a proguard rule affects a large number of classes.
 */
private const val TOO_MANY_AFFECTED_CLASSES_DESCRIPTION =
  "Scope rules using annotations, specific classes, or using specific field/method selectors"

/**
 * Reports when overly broad Proguard rules are used.
 *
 * Examples include:
 *
 * ```
 * -keep class com.**.*
 * -keep class **.* { <fields>; }
 * -keep class **.* { <methods>; }
 * ```
 */
class ExpensiveKeepRuleInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : ProguardR8Visitor() {
      override fun visitRuleWithClassSpecification(rule: ProguardR8RuleWithClassSpecification) {
        val expensiveKeepRule = expensiveKeepRuleOrNull(rule)
        if (expensiveKeepRule != null) {
          // Expand on this by using the qualified name to identify the blast radius.
          holder.registerProblem(
            expensiveKeepRule.elementToHighlight,
            expensiveKeepRule.description
          )
        }
      }
    }
  }
}

private data class ExpensiveProguardR8Rule(
  val elementToHighlight: PsiElement,
  val qualifiedName: ProguardR8QualifiedName,
  val description: String
)

private fun expensiveKeepRuleOrNull(rule: ProguardR8RuleWithClassSpecification): ExpensiveProguardR8Rule? {
  val service = rule.project.service<AffectedClassesProjectService>()
  val flag = rule.flag
  val header = rule.classSpecificationHeader
  val classType = header.childrenOfType<ProguardR8ClassType>().firstOrNull()
  val className = header.childrenOfType<ProguardR8ClassName>().firstOrNull()
  val isKeepRule = flag.textMatches("-keep")
  if (!isKeepRule) {
    // Only interested in unconditional keep rules at the moment.
    return null
  }
  val isClass = classType != null && classType.textMatches(/* text = */ "class")
  if (!isClass) {
    // Interfaces are not as problematic as class rules.
    return null
  }
  if (className == null) {
    // Early return. Nothing to do.
    return null
  }

  // Check for Negation in Rules
  // Check if the class name has a ! which represents negation of a rule.
  // These rules are super hard to reason about, and they cannot scale because 2 rules with negation is effectively keep everything.
  val negation = className.childrenOfType<PsiElement>()
    .firstOrNull { it.textMatches("!") }

  if (negation != null) {
    return ExpensiveProguardR8Rule(
      elementToHighlight = rule,
      qualifiedName = className.qualifiedName,
      description = "Rules that use negation typically end up keeping a lot more classes than intended, prefer one that does not use (!)"
    )
  }

  // Check if we have an annotation that scopes the rule.
  val hasAnnotation = header.childrenOfType<ProguardR8AnnotationName>().isNotEmpty()
  if (hasAnnotation) {
    // Nothing more to check
    return null
  }

  if (!className.qualifiedName.isExpensive()) {
    return null
  }

  // We have now identified that the name of the class has a wildcard.
  val countAffected = service.affectedClassesForQualifiedName(
    qualifiedName = className.qualifiedName
  )

  val body = rule.classSpecificationBody
  if (body == null) {
    return if (countAffected >= CLASSES_AFFECTED_LIMIT) {
      ExpensiveProguardR8Rule(
        elementToHighlight = rule,
        qualifiedName = className.qualifiedName,
        description = TOO_MANY_AFFECTED_CLASSES_DESCRIPTION
      )
    }
    else {
      null
    }
  }

  val isBodyExpensive = body.childrenOfType<ProguardR8JavaRule>()
    .any { proguardR8JavaRule ->
      val fieldSpecs = proguardR8JavaRule.childrenOfType<ProguardR8FieldsSpecification>()
      val fields = fieldSpecs.asSequence().flatMap { it.childrenOfType<ProguardR8Field>() }
      val methodSpecs = proguardR8JavaRule.childrenOfType<ProguardR8MethodSpecification>()

      fieldSpecs.any { it.isExpensive() } ||
      fields.any { it.isExpensive() } ||
      methodSpecs.any { it.isExpensive() }
    }



  if (isBodyExpensive && countAffected >= CLASSES_AFFECTED_LIMIT) {
    return ExpensiveProguardR8Rule(
      elementToHighlight = rule,
      qualifiedName = className.qualifiedName,
      description = TOO_MANY_AFFECTED_CLASSES_DESCRIPTION
    )
  }

  return null
}

internal fun ProguardR8QualifiedName.isExpensive(): Boolean {
  val elements = childrenOfType<PsiElement>()
  // We check if we have a doubleAsterisk for the last element
  val doubleAsteriskPresent = elements.firstOrNull { it.textMatches("**") } != null
  val lastPsiElementDoubleAsterisk = elements.lastOrNull()?.textMatches("**") ?: false
  // Or we check that we have a superfluous **(.*) additionally which means the same thing
  // We are excluding wildcards that include suffixes intentionally
  // E.g. **.*R$* is an example pattern that we are attempting to exclude.
  return lastPsiElementDoubleAsterisk ||
         (
           doubleAsteriskPresent &&
           elements.takeLast(n = 2).joinToString(separator = "") { it.text } == ".*"
         )
}

private fun ProguardR8FieldsSpecification.isExpensive(): Boolean {
  return childrenOfType<PsiElement>().any { it.textMatches("<fields>") }
}

private fun ProguardR8MethodSpecification.isExpensive(): Boolean {
  return childrenOfType<PsiElement>().any { it.textMatches("<methods>") }
}

private fun ProguardR8Field.isExpensive(): Boolean {
  return childrenOfType<ProguardR8ClassMemberName>()
    .flatMap { it.childrenOfType<PsiElement>() }
    .any { it.textMatches("**") || it.textMatches("*") }
}
