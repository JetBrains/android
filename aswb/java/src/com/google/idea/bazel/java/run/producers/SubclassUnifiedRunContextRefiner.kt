/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.bazel.java.run.producers

import com.google.idea.blaze.base.model.primitives.RuleType
import com.google.idea.blaze.base.run.producers.TargetSpecification
import com.google.idea.blaze.base.run.producers.TestFilterComponent
import com.google.idea.blaze.base.run.producers.TestFilterSyntax
import com.google.idea.blaze.base.run.producers.TestSelector
import com.google.idea.blaze.base.run.producers.UnifiedRunContext
import com.google.idea.blaze.base.run.producers.UnifiedRunContextRefiner
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

/** Interactive stage 2 refiner prompting user to choose a concrete subclass when running abstract test classes or methods. */
class SubclassUnifiedRunContextRefiner : UnifiedRunContextRefiner {

  override suspend fun refine(runContext: UnifiedRunContext, popupPosition: RelativePoint?): UnifiedRunContext {
    val psiClass = readAction { getPsiClass(runContext.sourceElement) } ?: return runContext
    val isAbstract = readAction { psiClass.hasModifierProperty(PsiModifier.ABSTRACT) }
    if (!isAbstract) {
      return runContext
    }

    val chosen = SubclassTestChooser.chooseSubclass(popupPosition, psiClass) ?: throw CancellationException("No subclass chosen")

    val refinedInfo =
      readAction {
        val className = chosen.qualifiedName ?: return@readAction null
        val methodName = getMethodName(runContext.sourceElement)
        val version = if (ProducerUtils.isJUnit4Class(chosen)) TestFilterSyntax.JUNIT_4 else TestFilterSyntax.JUNIT_3
        val vf = chosen.containingFile?.virtualFile ?: return@readAction null
        val newTargetSpec =
          TargetSpecification.PendingResolution(
            file = File(vf.path),
            testSize = TestSizeFinder.getTestSize(chosen),
            ruleType = RuleType.TEST,
          )
        RefinedInfo(className, methodName, version, newTargetSpec)
      } ?: return runContext

    val testSelector = TestSelector(refinedInfo.className, refinedInfo.methodName, null, refinedInfo.version)
    val newTestFilter = TestFilterComponent(listOf(testSelector), runContext.testFilter?.appendFilteredSuffix ?: false)

    return runContext.copy(
      sourceElement = chosen,
      target = refinedInfo.targetSpec,
      testFilter = newTestFilter,
      command = runContext.command,
    )
  }

  private data class RefinedInfo(
    val className: String,
    val methodName: String?,
    val version: TestFilterSyntax,
    val targetSpec: TargetSpecification,
  )

  private fun getPsiClass(element: Any): PsiClass? =
    when (element) {
      is PsiClass -> element
      is PsiMethod -> element.containingClass
      is KtClass -> element.toLightClass()
      is KtNamedFunction -> PsiTreeUtil.getParentOfType(element, KtClass::class.java, false)?.toLightClass()
      else -> null
    }

  private fun getMethodName(element: Any): String? =
    when (element) {
      is PsiMethod -> element.name
      is KtNamedFunction -> element.name
      else -> null
    }
}
