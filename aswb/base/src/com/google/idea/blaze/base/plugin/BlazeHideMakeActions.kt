/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.plugin

import com.android.tools.idea.startup.Actions.hideAction
import com.google.idea.blaze.base.settings.Blaze
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer

/** Runs on startup. */
class BlazeHideMakeActions : ActionConfigurationCustomizer {
  override fun customize(actionRegistrar: ActionManager) {
    // The original actions will be visible only on plain IDEA projects.
    val isBlazeProject: (AnActionEvent) -> Boolean = { e ->
      e.project?.let { Blaze.isBlazeProject(it) } == true
    }

    // 'Build' > 'Make Project' action
    hideAction(actionRegistrar, "CompileDirty", isBlazeProject)

    // 'Build' > 'Make Modules' action
    hideAction(actionRegistrar, IdeActions.ACTION_MAKE_MODULE, isBlazeProject)

    // 'Build' > 'Rebuild' action
    hideAction(actionRegistrar, IdeActions.ACTION_COMPILE_PROJECT, isBlazeProject)

    // 'Build' > 'Compile Modules' action
    hideAction(actionRegistrar, IdeActions.ACTION_COMPILE, isBlazeProject)

    // 'Build' > 'Recompile' action
    hideAction(actionRegistrar, "CompileFile", isBlazeProject)
  }
}
