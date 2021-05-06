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
package org.jetbrains.android.refactoring.namespaces

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.Project
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.usages.impl.rules.UsageGroupBase
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRuleProvider
import org.jetbrains.android.util.AndroidBundle

class ResourcePackageGroupingRuleProvider : UsageGroupingRuleProvider {

  override fun getActiveRules(project: Project): Array<UsageGroupingRule> {
    return if (StudioFlags.MIGRATE_TO_NON_TRANSITIVE_R_CLASSES_REFACTORING_ENABLED.get()) {
      arrayOf(ResourcePackageGroupingRule(), PropertiesFileGroupingRule())
    }
    else {
      UsageGroupingRule.EMPTY_ARRAY
    }
  }
}

class ResourcePackageGroupingRule : SingleParentUsageGroupingRule() {
  override fun getParentGroupFor(usage: Usage, targets: Array<UsageTarget>): UsageGroup? {
    val usageInfo = (usage as? UsageInfo2UsageAdapter)?.usageInfo as? CodeUsageInfo ?: return null
    val packageName = usageInfo.inferredPackage ?: return null
    return ResourcePackageUsageGroup(packageName)
  }

  override fun getRank(): Int = -1
}

class PropertiesFileGroupingRule : SingleParentUsageGroupingRule() {
  override fun getParentGroupFor(usage: Usage, targets: Array<UsageTarget>): UsageGroup? {
    val usageInfo = (usage as? UsageInfo2UsageAdapter)?.usageInfo as? PropertiesUsageInfo ?: return null
    return PropertiesFileUsageGroup(usageInfo.flag)
  }

  override fun getRank(): Int = -1
}

data class PropertiesFileUsageGroup(val flag: String) : UsageGroupBase(-1) {
  override fun getText(view: UsageView?): String = AndroidBundle.message("android.usageGroup.properties.new.flag", flag)
  override fun compareTo(other: UsageGroup) = -1
}

/**
 * [UsageGroup] for [MigrateToNonTransitiveRClassesProcessor].
 *
 * TODO(b/78765120): Make this work for [MigrateToResourceNamespacesProcessor] as well.
 */
data class ResourcePackageUsageGroup(val packageName: String) : UsageGroupBase(0) {
  override fun getText(view: UsageView?): String = AndroidBundle.message("android.usageGroup.resource.references.from.package", packageName)

  override fun compareTo(other: UsageGroup): Int {
    return if (other is ResourcePackageUsageGroup) {
      packageName.compareTo(other.packageName)
    }
    else {
      super.compareTo(other)
    }
  }
}
