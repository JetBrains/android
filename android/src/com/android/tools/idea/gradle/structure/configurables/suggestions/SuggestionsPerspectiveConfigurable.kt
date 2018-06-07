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

import com.android.tools.idea.gradle.structure.configurables.AbstractCounterDisplayConfigurable
import com.android.tools.idea.gradle.structure.configurables.BaseNamedConfigurable
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.PsAllModulesFakeModule
import com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.util.ui.UIUtil.invokeLaterIfNeeded
import javax.swing.JComponent

class SuggestionsPerspectiveConfigurable(context: PsContext) : AbstractCounterDisplayConfigurable(context) {
  private val configurablesByGradlePath: Map<String, BaseNamedConfigurable<PsModule>> = HashMap()
  private var extraTopModules: MutableList<PsModule>? = null
  private var messageCount: Int = 0

  override fun getId(): String = "android.psd.suggestions"

  override fun getConfigurable(module: PsModule): NamedConfigurable<out PsModule>? =
      when (module) {
        is PsAndroidModule -> getOrCreateConfigurable(module)
        is PsAllModulesFakeModule -> getOrCreateConfigurable(module)
        else -> null
      }

  override fun getNavigationPathName(): String = "suggestions.place"

  override fun getDisplayName(): String = "Suggestions"


  override fun getExtraTopModules(): MutableList<PsModule> {
    if (extraTopModules == null) {
      extraTopModules = mutableListOf(PsAllModulesFakeModule(context.project))
    }
    return extraTopModules!!
  }

  override fun getCount(): Int = messageCount

  override fun createComponent(): JComponent {
    val component = super.createComponent().apply { name = "SuggestionsView" }
    context.analyzerDaemon.add(PsAnalyzerDaemon.IssuesUpdatedListener {
      fireCountChangeListener()
      invokeLaterIfNeeded { messageCount = getIssues(context, null).size }
    }, this)
    return component
  }

  private fun getOrCreateConfigurable(module: PsModule) =
      configurablesByGradlePath[module.gradlePath] ?: createConfigurable(module)

  private fun createConfigurable(module: PsModule) =
      AndroidModuleSuggestionsConfigurable(context, module, getExtraTopModules()).apply { setHistory(myHistory) }
}
