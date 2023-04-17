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
package com.android.tools.idea.gradle.project.upgrade

import com.intellij.openapi.project.Project
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRuleProvider

/**
 * Usage Grouping by component.
 *
 * In [UsageView] and related classes (e.g. [UsageViewDescriptor], [UsageViewDescriptorAdapter]) there is the notion of grouping by
 * various attributes: file, module, and so on.
 *
 * Superficially, the grouping by Usage Type looks like we could adapt or override it to our purpose: to provide a grouping of usages
 * by component (on the assumption that that is a more meaningful grouping for the kind of whole-project refactoring that we are aiming
 * to provide.  However, the UsageView apparatus allows us only to compute a UsageType from [PsiElement]s, not [UsageInfo]s, and in general
 * we can have an arbitrary number of different [UsageInfo]s, with different semantics, related to the same [PsiElement].
 *
 * Therefore, we need our own [UsageGroupingRuleProvider].
 *
 * Unfortunately, the groups that providers can create cannot replace or interpose themselves between other provided rules.  We cannot
 * replace, as far as I can tell, the Usage Type grouping -- at least not without re-implementing everything (or the "gross hack" as in
 * ASwB's UsageGroupingRuleProviderOverride).  This is fine, in that it probably makes sense for the component grouping to be outermost,
 * but it does render the default [UsageViewDescriptorAdapter.getCodeReferencesText] meaningless, as the usagesCount/filesCount arguments
 * are apparently over the whole refactoring, not the group.
 */
class ComponentGroupingRuleProvider : UsageGroupingRuleProvider {
  override fun getActiveRules(project: Project): Array<UsageGroupingRule> = arrayOf(ComponentGroupingRule())
  // TODO(xof): do we need createGroupingActions()?
}

class ComponentGroupingRule : SingleParentUsageGroupingRule() {
  override fun getParentGroupFor(usage: Usage, targets: Array<out UsageTarget>): UsageGroup? {
    // TODO(xof): arguably we should have AgpComponentUsageInfo here
    val usageInfo = (usage as? UsageInfo2UsageAdapter)?.usageInfo as? GradleBuildModelUsageInfo ?: return null
    val wrappedElement = (usageInfo as? GradleBuildModelUsageInfo)?.element as? WrappedPsiElement ?: return null
    return ComponentUsageGroup(wrappedElement.processor.groupingName)
  }

  // The rank for this grouping rule is somewhat arbitrary.  It affects how the rule composes with other rules, but the
  // other rules that we expect to be applicable to our usages are the built-in ones (e.g. groups by module, by file, by
  // usage type).  As of platform version 2021.2 the built-in ones have effective rank between 0 and some hundreds, so as
  // long as the rank we return here is less than that, we will get the desired behaviour of the component groups being
  // closer to the tree root than built-in ones.  -42 is whimsically defensive against some other grouping rule coming along
  // with a rank of 0 or -1, while allowing smaller and larger ranks if necessary.
  override fun getRank(): Int = -42
}

data class ComponentUsageGroup(val usageName: String) : UsageGroup {

  override fun getPresentableGroupText(): String = usageName

  override fun compareTo(other: UsageGroup?): Int = when (other) {
    is ComponentUsageGroup -> usageName.compareTo(other.usageName)
    else -> -1
  }
}