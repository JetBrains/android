/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.suggestions

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.gradle.structure.configurables.AbstractCounterDisplayConfigurable
import com.android.tools.idea.gradle.structure.configurables.KmpModuleConfigurable
import com.android.tools.idea.gradle.structure.configurables.ModuleUnsupportedConfigurable
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.PsAllModulesFakeModule
import com.android.tools.idea.gradle.structure.configurables.android.modules.AbstractModuleConfigurable
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.structure.dialog.TrackedConfigurable
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.icons.AllIcons
import javax.swing.Icon
import javax.swing.JComponent

class SuggestionsPerspectiveConfigurable(context: PsContext)
  : AbstractCounterDisplayConfigurable(context, extraModules = listOf(PsAllModulesFakeModule(context.project))), TrackedConfigurable {
  private var messageCount: Int = 0
  private var errorCount: Int = 0

  init {
    fun issuesChanged() {
      val issues = getIssues(context, null)
      messageCount = issues.size
      errorCount = issues.count { it.severity == PsIssue.Severity.ERROR }
      fireCountChangeListener()
    }

    context.analyzerDaemon.onIssuesChange(this) @UiThread { issuesChanged() }
  }

  override val leftConfigurable = PSDEvent.PSDLeftConfigurable.PROJECT_STRUCTURE_DIALOG_LEFT_CONFIGURABLE_SUGGESTIONS

  override fun getId(): String = "android.psd.suggestions"

  override fun createConfigurableFor(module: PsModule): AbstractModuleConfigurable<PsModule, *> =
      when {
        module is PsAndroidModule && module.isKmpModule.not() -> createConfigurable(module)
        module is PsAndroidModule && module.isKmpModule -> KmpModuleConfigurable(context, this, module)
        module is PsJavaModule -> createConfigurable(module)
        module is PsAllModulesFakeModule -> createAllModulesConfigurable(module)
        else -> ModuleUnsupportedConfigurable(context, this, module)
      }

  override fun getDisplayName(): String = "Suggestions"

  override fun getCount(): Int = messageCount

  override fun containsErrors(): Boolean = errorCount > 0

  override fun createComponent(): JComponent = super.createComponent().apply { name = "SuggestionsView" }

  private fun createConfigurable(module: PsModule) =
      ModuleSuggestionsConfigurable(context, this, module).apply { setHistory(myHistory) }

  private fun createAllModulesConfigurable(module: PsModule) : ModuleSuggestionsConfigurable {
    return object : ModuleSuggestionsConfigurable(context, this@SuggestionsPerspectiveConfigurable, module) {
      override fun getIcon(expanded: Boolean): Icon? = AllIcons.Nodes.ModuleGroup
    }.apply { setHistory(myHistory) }
  }
}