/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.compose

import com.android.tools.idea.kotlin.hasAnnotation
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.usages.impl.rules.UsageGroupBase
import com.intellij.usages.impl.rules.UsageGroupingRulesDefaultRanks
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRuleEx
import com.intellij.usages.rules.UsageGroupingRuleProviderEx
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFunction
import javax.swing.Icon

private val PREVIEW_CLASS_ID = ClassId.fromString("androidx/compose/ui/tooling/preview/Preview")

/** Returns whether a [PsiElement] is used within a Kotlin function annotated with @Preview. */
private tailrec fun PsiElement.isInPreviewFunction(): Boolean {
  val parentFunction = parentOfType<KtFunction>(withSelf = false) ?: return false

  if (parentFunction.hasComposableAnnotation() && parentFunction.hasPreviewAnnotation()) return true

  return parentFunction.isInPreviewFunction()
}

/** Returns whether a [KtFunction] is annotated with @Preview. */
private fun KtFunction.hasPreviewAnnotation() = hasAnnotation(PREVIEW_CLASS_ID)

/** Returns whether any of the [UsageTarget]s represent @Composable functions. */
private fun Array<out UsageTarget>.containsComposable(): Boolean =
  asSequence()
    .filterIsInstance<PsiElementUsageTarget>()
    .mapNotNull { it.element as? KtFunction }
    .any { it.hasComposableAnnotation() }

class ComposeUsageGroupingRuleProvider : UsageGroupingRuleProviderEx {
  override fun getActiveRules(project: Project): Array<UsageGroupingRule> =
    arrayOf(PreviewUsageGroupingRule)

  override fun getAllRules(project: Project, usageView: UsageView?): Array<UsageGroupingRule> =
    arrayOf(PreviewUsageGroupingRule)
}

private object PreviewUsageGroupingRule : UsageGroupingRuleEx {
  // TODO(b/279446921): Replace with @Preview icon when available.
  override fun getIcon() = null

  override fun getRank() = UsageGroupingRulesDefaultRanks.BEFORE_USAGE_TYPE.absoluteRank

  override fun getTitle() = ComposeBundle.message("separate.preview.usages")

  override fun getParentGroupsFor(usage: Usage, targets: Array<out UsageTarget>): List<UsageGroup> {
    // This block exists to facilitate end-to-end testing for ShowUsages. When ShowUsages is
    // invoked, irrespective of whether anything
    // related to compose is happening, this code will execute for each Usage seen, logging
    // something to idea.log we can look for in our
    // end-to-end test, provided we turn on debugging for this class.
    if (java.lang.Boolean.getBoolean("studio.run.under.integration.test")) {
      Logger.getInstance(ComposeUsageGroupingRuleProvider::class.java)
        .debug("Saw usage: ${usage.presentation.plainText.trim()}")
    }

    val element = (usage as? PsiElementUsage)?.element ?: return emptyList()
    return when {
      !targets.containsComposable() -> emptyList()
      element.isInPreviewFunction() -> listOf(PreviewUsageGroup)
      else -> listOf(ProductionUsageGroup)
    }
  }
}

internal object PreviewUsageGroup : UsageGroupBase(1) {
  // TODO(b/279446921): Replace with @Preview icon when available.
  override fun getIcon(): Icon? = null

  override fun getPresentableGroupText() = ComposeBundle.message("usage.group.in.preview.function")
}

internal object ProductionUsageGroup : UsageGroupBase(0) {
  override fun getIcon(): Icon? = null

  override fun getPresentableGroupText() =
    ComposeBundle.message("usage.group.in.nonpreview.function")
}
